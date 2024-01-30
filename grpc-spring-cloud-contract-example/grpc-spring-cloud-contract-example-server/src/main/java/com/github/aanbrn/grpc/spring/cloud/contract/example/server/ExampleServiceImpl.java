package com.github.aanbrn.grpc.spring.cloud.contract.example.server;

import com.github.aanbrn.spring.cloud.contract.example.BidiStreamingRequest;
import com.github.aanbrn.spring.cloud.contract.example.BidiStreamingResponse;
import com.github.aanbrn.spring.cloud.contract.example.ClientStreamingRequest;
import com.github.aanbrn.spring.cloud.contract.example.ClientStreamingResponse;
import com.github.aanbrn.spring.cloud.contract.example.ExampleServiceGrpc.ExampleServiceImplBase;
import com.github.aanbrn.spring.cloud.contract.example.ServerStreamingRequest;
import com.github.aanbrn.spring.cloud.contract.example.ServerStreamingResponse;
import com.github.aanbrn.spring.cloud.contract.example.UnaryRequest;
import com.github.aanbrn.spring.cloud.contract.example.UnaryResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
class ExampleServiceImpl extends ExampleServiceImplBase {
    @Override
    public void unaryMethod(@NonNull UnaryRequest request, @NonNull StreamObserver<UnaryResponse> responseObserver) {
        responseObserver.onNext(
                UnaryResponse
                        .newBuilder()
                        .setValue(request.getValue())
                        .build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<ClientStreamingRequest> clientStreamingMethod(
            @NonNull StreamObserver<ClientStreamingResponse> responseObserver) {
        return new StreamObserver<>() {

            private final ClientStreamingResponse.Builder responseBuilder = ClientStreamingResponse.newBuilder();

            @Override
            public void onNext(@NonNull ClientStreamingRequest request) {
                responseBuilder.addValue(request.getValue());
            }

            @Override
            public void onError(@NonNull Throwable t) {
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
            @NonNull ServerStreamingRequest request,
            @NonNull StreamObserver<ServerStreamingResponse> responseObserver) {
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
            public void onNext(@NonNull BidiStreamingRequest request) {
                responseObserver.onNext(
                        BidiStreamingResponse
                                .newBuilder()
                                .setValue(request.getValue())
                                .build());
            }

            @Override
            public void onError(@NonNull Throwable t) {
                throw new StatusRuntimeException(Status.fromThrowable(t));
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
