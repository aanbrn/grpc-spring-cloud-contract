package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpResponder;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;

import java.io.IOException;
import java.util.List;

import static com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcUtils.messageFromJson;
import static com.github.aanbrn.grpc.spring.cloud.contract.util.GrpcUtils.messagesFromJson;
import static java.lang.Integer.parseInt;

abstract class GrpcWireMockMethodHandlerBase {

    protected final HttpResponder unaryMessageResponder(
            @NonNull MethodDescriptor methodDescriptor, @NonNull StreamObserver<DynamicMessage> responseObserver) {
        return (request, response) -> {
            HttpHeader statusHeader = response.getHeaders().getHeader("grpc-status");
            if (!statusHeader.isPresent()
                    || Status.fromCodeValue(parseInt(statusHeader.firstValue())) == Status.OK) {
                DynamicMessage outputMessage;
                try {
                    outputMessage = messageFromJson(response.getBodyAsString(), methodDescriptor.getOutputType());
                } catch (IOException e) {
                    throw new IllegalStateException("Response body must contain a valid output message", e);
                }
                responseObserver.onNext(outputMessage);
                responseObserver.onCompleted();
            } else {
                Status status = Status.fromCodeValue(parseInt(statusHeader.firstValue()));
                HttpHeader messageHeader = response.getHeaders().getHeader("grpc-message");
                if (messageHeader.isPresent()) {
                    status = status.withDescription(messageHeader.firstValue());
                }
                responseObserver.onError(status.asRuntimeException());
            }
        };
    }

    protected final HttpResponder streamingMessagesResponder(
            @NonNull MethodDescriptor methodDescriptor, @NonNull StreamObserver<DynamicMessage> responseObserver) {
        return (request, response) -> {
            HttpHeader statusHeader = response.getHeaders().getHeader("grpc-status");
            if (!statusHeader.isPresent()
                    || Status.fromCodeValue(parseInt(statusHeader.firstValue())) == Status.OK) {
                List<DynamicMessage> outputMessages;
                try {
                    outputMessages = messagesFromJson(response.getBodyAsString(), methodDescriptor.getOutputType());
                } catch (IOException e) {
                    throw new IllegalStateException("Response body must contain an array of valid output messages", e);
                }
                outputMessages.forEach(responseObserver::onNext);
                responseObserver.onCompleted();
            } else {
                Status status = Status.fromCodeValue(parseInt(statusHeader.firstValue()));
                HttpHeader messageHeader = response.getHeaders().getHeader("grpc-message");
                if (messageHeader.isPresent()) {
                    status = status.withDescription(messageHeader.firstValue());
                }
                responseObserver.onError(status.asRuntimeException());
            }
        };
    }
}
