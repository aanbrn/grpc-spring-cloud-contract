package com.github.aanbrn.grpc.spring.cloud.contract.example.server;

import com.github.aanbrn.spring.cloud.contract.example.*;
import com.github.aanbrn.spring.cloud.contract.example.ExampleServiceGrpc.ExampleServiceImplBase;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
class ExampleServiceImpl extends ExampleServiceImplBase {
    @Override
    public void unaryMethod(
            @NonNull final UnaryRequest request, @NonNull final StreamObserver<UnaryResponse> responseObserver) {
        responseObserver.onNext(
                UnaryResponse
                        .newBuilder()
                        .setValue(request.getValue())
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<ClientStreamingRequest> clientStreamingMethod(
            @NonNull final StreamObserver<ClientStreamingResponse> responseObserver) {
        return new StreamObserver<>() {

            private final ClientStreamingResponse.Builder responseBuilder = ClientStreamingResponse.newBuilder();

            @Override
            public void onNext(@NonNull final ClientStreamingRequest request) {
                responseBuilder.addValue(request.getValue());
            }

            @Override
            public void onError(@NonNull final Throwable t) {
                throw new StatusRuntimeException(Status.fromThrowable(t));
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(responseBuilder.build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public void serverStreamingMethod(
            @NonNull final ServerStreamingRequest request,
            @NonNull final StreamObserver<ServerStreamingResponse> responseObserver) {
        for (int i = 0; i < request.getValueCount(); i++) {
            responseObserver.onNext(
                    ServerStreamingResponse
                            .newBuilder()
                            .setValue(request.getValue(i))
                            .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<BidiStreamingRequest> bidiStreamingMethod(
            StreamObserver<BidiStreamingResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(@NonNull final BidiStreamingRequest request) {
                responseObserver.onNext(
                        BidiStreamingResponse
                                .newBuilder()
                                .setValue(request.getValue())
                                .build());
            }

            @Override
            public void onError(@NonNull final Throwable t) {
                throw new StatusRuntimeException(Status.fromThrowable(t));
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
