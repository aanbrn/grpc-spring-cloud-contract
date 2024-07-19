package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.google.protobuf.DynamicMessage;
import io.grpc.InternalChannelz.SocketStats;
import io.grpc.*;
import io.grpc.ServerStreamTracer.Factory;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.InternalServer;
import io.grpc.internal.ServerImplBuilder;
import io.grpc.internal.ServerListener;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import lombok.NonNull;
import lombok.val;
import wiremock.javax.servlet.ServletException;
import wiremock.javax.servlet.http.HttpServletRequest;
import wiremock.javax.servlet.http.HttpServletResponse;
import wiremock.org.eclipse.jetty.server.Handler;
import wiremock.org.eclipse.jetty.server.Request;
import wiremock.org.eclipse.jetty.server.Response;
import wiremock.org.eclipse.jetty.server.handler.HandlerWrapper;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static shaded.com.google.common.base.Preconditions.checkArgument;
import static shaded.com.google.common.base.Preconditions.checkState;

class GrpcWireMockHandler extends HandlerWrapper implements InternalServer {

    private final Server server;

    private List<? extends Factory> streamTracerFactories;

    private final AtomicReference<ServerListener> serverListener = new AtomicReference<>();

    GrpcWireMockHandler(
            @NonNull final Handler handler,
            @NonNull final StubRequestHandler stubRequestHandler,
            @NonNull final Collection<ServiceDescriptor> services) {
        checkArgument(!services.isEmpty(), "Argument 'services' cannot be empty");

        setHandler(handler);

        val serverBuilder = new ServerImplBuilder(streamTracerFactories -> {
            this.streamTracerFactories = List.copyOf(streamTracerFactories);
            return GrpcWireMockHandler.this;
        });

        for (val service : services) {
            val serverServiceDefinition = ServerServiceDefinition.builder(service.getName());

            for (val method : service.getMethods()) {
                checkState(method.getSchemaDescriptor() instanceof ProtoMethodDescriptorSupplier,
                        "No schema descriptor for the gRPC method: " + method);

                val protoMethod =
                        ((ProtoMethodDescriptorSupplier) method.getSchemaDescriptor()).getMethodDescriptor();
                val methodRequestMarshaller = ProtoUtils.marshaller(
                        DynamicMessage.getDefaultInstance(protoMethod.getInputType()));
                val methodResponseMarshaller = ProtoUtils.marshaller(
                        DynamicMessage.getDefaultInstance(protoMethod.getOutputType()));

                serverServiceDefinition.addMethod(
                        ServerMethodDefinition.create(
                                method.toBuilder(methodRequestMarshaller, methodResponseMarshaller).build(),
                                switch (method.getType()) {
                                    case UNARY -> ServerCalls.asyncUnaryCall(
                                            new GrpcWireMockUnaryMethodHandler(
                                                    protoMethod, stubRequestHandler));
                                    case SERVER_STREAMING -> ServerCalls.asyncServerStreamingCall(
                                            new GrpcWireMockServerStreamingMethodHandler(
                                                    protoMethod, stubRequestHandler));
                                    case CLIENT_STREAMING -> ServerCalls.asyncClientStreamingCall(
                                            new GrpcWireMockClientStreamingMethodHandler(
                                                    protoMethod, stubRequestHandler));
                                    case BIDI_STREAMING -> ServerCalls.asyncBidiStreamingCall(
                                            new GrpcWireMockBidiStreamingMethodHandler(
                                                    protoMethod, stubRequestHandler));
                                    default -> throw new IllegalStateException(
                                            "Unknown gRPC method type: " + method.getType());
                                }));
            }

            serverBuilder.addService(serverServiceDefinition.build());
        }

        this.server = serverBuilder.build();
    }

    @Override
    public void start(@NonNull final ServerListener serverListener) {
        this.serverListener.set(serverListener);
    }

    @Override
    public void shutdown() {
        val serverListener = this.serverListener.getAndSet(null);
        if (serverListener != null) {
            serverListener.serverShutdown();
        }
    }

    @Override
    public SocketAddress getListenSocketAddress() {
        return new SocketAddress() {
            @Override
            public String toString() {
                return GrpcWireMockHandler.class.getName();
            }
        };
    }

    @Override
    public List<? extends SocketAddress> getListenSocketAddresses() {
        return List.of(getListenSocketAddress());
    }

    @Nullable
    @Override
    public InternalInstrumented<SocketStats> getListenSocketStats() {
        return null;
    }

    @Nullable
    @Override
    public List<InternalInstrumented<SocketStats>> getListenSocketStatsList() {
        return null;
    }

    @Override
    public void handle(
            final String target, final Request baseRequest, final HttpServletRequest request,
            final HttpServletResponse response) throws IOException, ServletException {
        if (!GrpcUtil.isGrpcContentType(baseRequest.getContentType())) {
            super.handle(target, baseRequest, request, response);
            return;
        }

        val serverListener = this.serverListener.get();
        checkState(serverListener != null, "gRPC server must be running");

        val serverTransport =
                new GrpcWireMockServerTransport(baseRequest, (Response) response, streamTracerFactories);
        serverTransport.start(serverListener.transportCreated(serverTransport));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        this.server.start();
    }

    @Override
    protected void doStop() throws Exception {
        this.server.shutdown();

        super.doStop();
    }
}
