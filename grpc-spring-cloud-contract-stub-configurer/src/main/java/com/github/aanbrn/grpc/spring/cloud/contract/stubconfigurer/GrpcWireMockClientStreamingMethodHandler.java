package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCalls.ClientStreamingMethod;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
class GrpcWireMockClientStreamingMethodHandler
        extends GrpcWireMockMethodHandlerBase implements ClientStreamingMethod<DynamicMessage, DynamicMessage> {

    @NonNull
    private final MethodDescriptor methodDescriptor;

    @NonNull
    private final StubRequestHandler stubRequestHandler;

    @Override
    public StreamObserver<DynamicMessage> invoke(final StreamObserver<DynamicMessage> responseObserver) {
        return new StreamObserver<>() {

            private final List<DynamicMessage> inputMessages = new ArrayList<>();

            @Override
            public void onNext(DynamicMessage inputMessage) {
                inputMessages.add(inputMessage);
            }

            @Override
            public void onError(Throwable t) {
                throw new StatusRuntimeException(Status.fromThrowable(t));
            }

            @Override
            public void onCompleted() {
                stubRequestHandler.handle(
                        new GrpcWireMockRequest(methodDescriptor, inputMessages),
                        unaryResponder(methodDescriptor, responseObserver));
            }
        };
    }
}
