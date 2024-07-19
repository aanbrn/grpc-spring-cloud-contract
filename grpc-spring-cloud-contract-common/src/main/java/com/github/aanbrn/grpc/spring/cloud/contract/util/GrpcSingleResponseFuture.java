package com.github.aanbrn.grpc.spring.cloud.contract.util;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;

public class GrpcSingleResponseFuture<T extends Message> extends AbstractFuture<T>
        implements StreamObserver<T> {

    private T message;

    @Override
    public void onNext(final T message) {
        if (this.message == null) {
            this.message = message;
        }
    }

    @Override
    public void onError(final Throwable t) {
        setException(t);
    }

    @Override
    public void onCompleted() {
        set(message);
    }
}
