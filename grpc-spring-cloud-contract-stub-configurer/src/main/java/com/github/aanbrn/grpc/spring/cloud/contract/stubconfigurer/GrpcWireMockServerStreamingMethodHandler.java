package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.DynamicMessage;
import io.grpc.stub.ServerCalls.ServerStreamingMethod;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class GrpcWireMockServerStreamingMethodHandler
        extends GrpcWireMockMethodHandlerBase implements ServerStreamingMethod<DynamicMessage, DynamicMessage> {

    @NonNull
    private final MethodDescriptor methodDescriptor;

    @NonNull
    private final StubRequestHandler stubRequestHandler;

    @Override
    public void invoke(DynamicMessage inputMessage, StreamObserver<DynamicMessage> responseObserver) {
        stubRequestHandler.handle(
                new GrpcWireMockRequest(methodDescriptor, inputMessage),
                streamingMessagesResponder(methodDescriptor, responseObserver));
    }
}
