package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.github.tomakehurst.wiremock.http.StubRequestHandler;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.grpc.InternalChannelz.SocketStats;
import io.grpc.InternalInstrumented;
import io.grpc.MethodDescriptor;
import io.grpc.MethodDescriptor.Marshaller;
import io.grpc.Server;
import io.grpc.ServerMethodDefinition;
import io.grpc.ServerServiceDefinition;
import io.grpc.ServerStreamTracer.Factory;
import io.grpc.ServiceDescriptor;
import io.grpc.internal.GrpcUtil;
import io.grpc.internal.InternalServer;
import io.grpc.internal.ServerImplBuilder;
import io.grpc.internal.ServerListener;
import io.grpc.protobuf.ProtoMethodDescriptorSupplier;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ServerCalls;
import lombok.NonNull;
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

import static shaded.com.google.common.base.Preconditions.checkArgument;
import static shaded.com.google.common.base.Preconditions.checkState;

class GrpcWireMockHandler extends HandlerWrapper implements InternalServer {

    private final Server server;

    private List<? extends Factory> streamTracerFactories;

    private volatile ServerListener serverListener;

    GrpcWireMockHandler(
            @NonNull Handler handler,
            @NonNull StubRequestHandler stubRequestHandler,
            @NonNull Collection<ServiceDescriptor> services) {
        checkArgument(!services.isEmpty(), "Argument 'services' cannot be empty");

        setHandler(handler);

        ServerImplBuilder serverBuilder = new ServerImplBuilder(streamTracerFactories -> {
            this.streamTracerFactories = streamTracerFactories;
            return GrpcWireMockHandler.this;
        });

        for (ServiceDescriptor service : services) {
            ServerServiceDefinition.Builder serverServiceDefinition =
                    ServerServiceDefinition.builder(service.getName());

            for (MethodDescriptor<?, ?> method : service.getMethods()) {
                checkState(method.getSchemaDescriptor() instanceof ProtoMethodDescriptorSupplier,
                           "No schema descriptor for the gRPC method: " + method);

                Descriptors.MethodDescriptor protoMethod =
                        ((ProtoMethodDescriptorSupplier) method.getSchemaDescriptor()).getMethodDescriptor();
                Marshaller<DynamicMessage> methodRequestMarshaller = ProtoUtils.marshaller(
                        DynamicMessage.getDefaultInstance(protoMethod.getInputType()));
                Marshaller<DynamicMessage> methodResponseMarshaller = ProtoUtils.marshaller(
                        DynamicMessage.getDefaultInstance(protoMethod.getOutputType()));

                serverServiceDefinition.addMethod(
                        ServerMethodDefinition.create(
                                method.toBuilder(methodRequestMarshaller, methodResponseMarshaller)
                                          .build(),
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
    public void start(@NonNull ServerListener serverListener) {
        this.serverListener = serverListener;
    }

    @Override
    public void shutdown() {
        if (this.serverListener != null) {
            synchronized (this) {
                if (this.serverListener != null) {
                    try {
                        this.serverListener.serverShutdown();
                    } finally {
                        this.serverListener = null;
                    }
                }
            }
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
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if (!GrpcUtil.isGrpcContentType(baseRequest.getContentType())) {
            super.handle(target, baseRequest, request, response);
            return;
        }

        checkState(serverListener != null, "gRPC server must be running");

        GrpcWireMockServerTransport serverTransport =
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
