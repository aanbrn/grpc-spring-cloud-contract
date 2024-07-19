package com.github.aanbrn.grpc.spring.cloud.contract.verifier;

import com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcMultipleResponseFuture;
import com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcSingleResponseFuture;
import com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcUtils;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.DynamicMessage;
import io.grpc.*;
import io.grpc.internal.GrpcUtil;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import lombok.NonNull;
import lombok.val;
import org.springframework.cloud.contract.spec.internal.DslProperty;
import org.springframework.cloud.contract.spec.internal.HttpHeaders;
import org.springframework.cloud.contract.spec.internal.HttpMethods.HttpMethod;
import org.springframework.cloud.contract.spec.internal.HttpStatus;
import org.springframework.cloud.contract.verifier.http.HttpVerifier;
import org.springframework.cloud.contract.verifier.http.Request;
import org.springframework.cloud.contract.verifier.http.Response;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcUtils.*;
import static shaded.com.google.common.base.Preconditions.checkArgument;

public class GrpcHttpVerifier implements HttpVerifier {

    private final Channel channel;

    private final Map<String, MethodDescriptor<?, ?>> methods = new HashMap<>();

    public GrpcHttpVerifier(@NonNull final Channel channel, @NonNull final Collection<BindableService> services) {
        checkArgument(!services.isEmpty(), "Argument 'services' cannot be empty");

        this.channel = channel;

        for (val service : services) {
            val serverServiceDefinition = service.bindService();
            for (val serverMethodDefinition : serverServiceDefinition.getMethods()) {
                val methodDescriptor = serverMethodDefinition.getMethodDescriptor();
                methods.put(methodDescriptor.getFullMethodName(), methodDescriptor);
            }
        }
    }

    @Override
    public Response exchange(@NonNull final Request request) {
        if (request.method() != null && request.method() != HttpMethod.POST) {
            throw new IllegalArgumentException("POST request method is supported only");
        }
        if (request.headers().containsKey(HttpHeaders.CONTENT_TYPE)
                && !GrpcUtil.isGrpcContentType((String) request.headers().get(HttpHeaders.CONTENT_TYPE))) {
            throw new IllegalArgumentException("gRPC content type is supported only");
        }
        if (request.queryParams() != null && !request.queryParams().isEmpty()) {
            throw new IllegalArgumentException("Query parameters are not supported");
        }
        if (request.cookies() != null && !request.cookies().isEmpty()) {
            throw new IllegalArgumentException("Cookies are not supported");
        }
        if (request.path() == null && request.path().isBlank()) {
            throw new IllegalArgumentException("Request path is required");
        }
        if (request.path().charAt(0) != '/') {
            throw new IllegalArgumentException("Request path must start with a slash");
        }
        if (request.body() == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        val methodName = GrpcUtils.extractMethodName(request.path());

        val grpcMethod = methods.get(methodName);
        if (grpcMethod == null) {
            throw new IllegalStateException("No gRPC method related to the given path");
        }
        if (!(grpcMethod.getSchemaDescriptor() instanceof ProtoMethodDescriptorSupplier)) {
            throw new IllegalStateException("No proto schema descriptor for the related gRPC method");
        }
        val protoMethod =
                ((ProtoMethodDescriptorSupplier) grpcMethod.getSchemaDescriptor()).getMethodDescriptor();
        if (protoMethod == null) {
            throw new IllegalStateException("No proto method descriptor for the related gRPC method");
        }

        val inputMessageMarshaller =
                ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(protoMethod.getInputType()));
        val outputMessageMarshaller =
                ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(protoMethod.getOutputType()));

        val call =
                channel.newCall(
                        grpcMethod.toBuilder(inputMessageMarshaller, outputMessageMarshaller)
                                .build(),
                        CallOptions.DEFAULT);

        return switch (grpcMethod.getType()) {
            case UNARY -> unaryExchange(call, request, protoMethod.getInputType());
            case CLIENT_STREAMING -> clientStreamingExchange(call, request, protoMethod.getInputType());
            case SERVER_STREAMING -> serverStreamingExchange(call, request, protoMethod.getInputType());
            case BIDI_STREAMING -> bidiStreamingExchange(call, request, protoMethod.getInputType());
            default -> throw new IllegalStateException("Unknown gRPC method type: " + grpcMethod.getType());
        };
    }

    private Response unaryExchange(
            @NonNull final ClientCall<DynamicMessage, DynamicMessage> call,
            @NonNull final Request request,
            @NonNull final Descriptor inputMessageType) {
        final DynamicMessage inputMessage;
        try {
            inputMessage = messageFromJson(request.body().asString(), inputMessageType);
        } catch (IOException e) {
            throw new IllegalStateException("Request body must contain a valid input message", e);
        }

        try {
            val outputMessage = ClientCalls.blockingUnaryCall(call, inputMessage);
            return Response
                    .builder()
                    .statusCode(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, GrpcUtil.CONTENT_TYPE_GRPC)
                    .header("grpc-encoding", "identity")
                    .header("grpc-accept-encoding", "gzip")
                    .body(messageAsMap(outputMessage).toString())
                    .build();
        } catch (Exception e) {
            val status = Status.fromThrowable(e);
            return Response
                    .builder()
                    .statusCode(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, GrpcUtil.CONTENT_TYPE_GRPC)
                    .header("grpc-status", status.getCode().value())
                    .header("grpc-message", status.getDescription())
                    .build();
        }
    }

    private Response clientStreamingExchange(
            @NonNull final ClientCall<DynamicMessage, DynamicMessage> call,
            @NonNull final Request request,
            @NonNull final Descriptor inputMessageType) {
        final List<DynamicMessage> inputMessages;
        try {
            inputMessages = messagesFromJson(request.body().asString(), inputMessageType);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Request body must contain an array of valid input messages", e);
        }

        try {
            val outputFuture = new GrpcSingleResponseFuture<DynamicMessage>();
            val inputObserver = ClientCalls.asyncClientStreamingCall(call, outputFuture);
            inputMessages.forEach(inputObserver::onNext);
            inputObserver.onCompleted();

            val outputMessage = outputFuture.get();
            return Response
                    .builder()
                    .statusCode(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, GrpcUtil.CONTENT_TYPE_GRPC)
                    .header("grpc-encoding", "identity")
                    .header("grpc-accept-encoding", "gzip")
                    .body(messageAsMap(outputMessage).toString())
                    .build();
        } catch (Exception e) {
            val status = Status.fromThrowable(e);
            return Response
                    .builder()
                    .statusCode(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, GrpcUtil.CONTENT_TYPE_GRPC)
                    .header("grpc-status", status.getCode().value())
                    .header("grpc-message", status.getDescription())
                    .build();
        }
    }

    private Response serverStreamingExchange(
            @NonNull final ClientCall<DynamicMessage, DynamicMessage> call,
            @NonNull final Request request,
            @NonNull final Descriptor inputMessageType) {
        final DynamicMessage inputMessage;
        try {
            inputMessage = messageFromJson(request.body().asString(), inputMessageType);
        } catch (IOException e) {
            throw new IllegalStateException("Request body must contain a valid input message", e);
        }

        try {
            val outputMessages = ClientCalls.blockingServerStreamingCall(call, inputMessage);
            return Response
                    .builder()
                    .statusCode(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, GrpcUtil.CONTENT_TYPE_GRPC)
                    .header("grpc-encoding", "identity")
                    .header("grpc-accept-encoding", "gzip")
                    .body(messagesAsList(outputMessages)
                            .stream()
                            .map(DslProperty::new)
                            .toList()
                            .toString())
                    .build();
        } catch (Exception e) {
            val status = Status.fromThrowable(e);
            return Response
                    .builder()
                    .statusCode(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, GrpcUtil.CONTENT_TYPE_GRPC)
                    .header("grpc-status", status.getCode().value())
                    .header("grpc-message", status.getDescription())
                    .build();
        }
    }

    private Response bidiStreamingExchange(
            @NonNull final ClientCall<DynamicMessage, DynamicMessage> call,
            @NonNull final Request request,
            @NonNull final Descriptor inputMessageType) {
        final List<DynamicMessage> inputMessages;
        try {
            inputMessages = messagesFromJson(request.body().asString(), inputMessageType);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Request body must contain an array of valid input messages", e);
        }

        try {
            val outputFuture = new GrpcMultipleResponseFuture<DynamicMessage>();
            val inputObserver = ClientCalls.asyncBidiStreamingCall(call, outputFuture);
            inputMessages.forEach(inputObserver::onNext);
            inputObserver.onCompleted();

            val outputMessages = outputFuture.get();
            return Response
                    .builder()
                    .statusCode(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, GrpcUtil.CONTENT_TYPE_GRPC)
                    .header("grpc-encoding", "identity")
                    .header("grpc-accept-encoding", "gzip")
                    .body(messagesAsList(outputMessages)
                            .stream()
                            .map(DslProperty::new)
                            .toList()
                            .toString())
                    .build();
        } catch (Exception e) {
            val status = Status.fromThrowable(e);
            return Response
                    .builder()
                    .statusCode(HttpStatus.OK)
                    .header(HttpHeaders.CONTENT_TYPE, GrpcUtil.CONTENT_TYPE_GRPC)
                    .header("grpc-status", status.getCode().value())
                    .header("grpc-message", status.getDescription())
                    .build();
        }
    }
}