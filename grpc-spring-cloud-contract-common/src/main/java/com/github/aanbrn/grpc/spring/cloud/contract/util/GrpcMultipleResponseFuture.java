package com.github.aanbrn.grpc.spring.cloud.contract.util;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.List;

public class GrpcMultipleResponseFuture<T extends Message> extends AbstractFuture<List<T>>
        implements StreamObserver<T> {

    private final List<T> messages = new ArrayList<>();

    @Override
    public void onNext(T message) {
        messages.add(message);
    }

    @Override
    public void onError(Throwable t) {
        setException(t);
    }

    @Override
    public void onCompleted() {
        set(List.copyOf(messages));
    }
}
