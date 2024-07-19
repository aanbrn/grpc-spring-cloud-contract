package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer.GrpcWireMockServerStream.GrpcWireMockServerTransportState;
import com.google.common.util.concurrent.ListenableFuture;
import io.grpc.Attributes;
import io.grpc.Grpc;
import io.grpc.InternalChannelz.SocketStats;
import io.grpc.InternalLogId;
import io.grpc.ServerStreamTracer.Factory;
import io.grpc.Status;
import io.grpc.internal.*;
import lombok.*;
import wiremock.javax.servlet.ReadListener;
import wiremock.javax.servlet.ServletInputStream;
import wiremock.org.eclipse.jetty.server.AsyncContextState;
import wiremock.org.eclipse.jetty.server.Request;
import wiremock.org.eclipse.jetty.server.Response;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import static shaded.com.google.common.base.Preconditions.checkState;
import static wiremock.org.apache.commons.io.IOUtils.closeQuietly;

@RequiredArgsConstructor
class GrpcWireMockServerTransport implements ServerTransport {

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private class GrpcWireMockServerTransportSource implements ReadListener {

        @NonNull
        private final ServletInputStream input;

        private final byte[] buffer = new byte[4 * 1024];

        private void start() {
            input.setReadListener(this);
        }

        @Override
        public void onDataAvailable() throws IOException {
            while (input.isReady()) {
                val length = input.read(buffer);
                if (length == -1) {
                    break;
                }
                stream.transportState().runOnTransportThread(() -> stream.transportState().inboundDataReceived(
                        ReadableBuffers.wrap(Arrays.copyOf(buffer, length)), false));
            }
        }

        @Override
        public void onAllDataRead() {
            closeQuietly(input);

            stream.transportState().runOnTransportThread(() -> stream.transportState().inboundDataReceived(
                    ReadableBuffers.empty(), true));
        }

        @Override
        public void onError(@NonNull Throwable t) {
            closeQuietly(input);

            stream.cancel(Status.fromThrowable(t));
        }
    }

    @Getter
    private final InternalLogId logId = InternalLogId.allocate(GrpcWireMockServerTransport.class, null);

    @Getter
    private final ScheduledExecutorService scheduledExecutorService = SharedResourceHolder.get(GrpcUtil.TIMER_SERVICE);

    @NonNull
    private final Request request;

    @NonNull
    private final Response response;

    @NonNull
    private final List<? extends Factory> streamTracerFactories;

    private ServerTransportListener serverTransportListener;

    private GrpcWireMockServerStream stream;

    void start(@NonNull final ServerTransportListener serverTransportListener) throws IOException {
        this.serverTransportListener = serverTransportListener;

        checkState(request.isAsyncSupported(), "Request must support asynchronous operation");

        val methodName = GrpcWireMockUtils.extractMethodName(request);
        val headers = GrpcWireMockUtils.extractHeaders(request);

        final SocketAddress localAddress;
        try {
            localAddress = new InetSocketAddress(
                    InetAddress.getByName(request.getLocalAddr()), request.getLocalPort());
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
        final SocketAddress remoteAddress;
        try {
            remoteAddress = new InetSocketAddress(
                    InetAddress.getByName(request.getRemoteAddr()), request.getRemotePort());
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }

        this.serverTransportListener.transportReady(
                Attributes.newBuilder()
                        .set(Grpc.TRANSPORT_ATTR_LOCAL_ADDR, localAddress)
                        .set(Grpc.TRANSPORT_ATTR_REMOTE_ADDR, remoteAddress)
                        .build());

        val asyncContext = (AsyncContextState) request.startAsync(request, response);

        val statsTraceContext = StatsTraceContext.newServerContext(streamTracerFactories, methodName, headers);

        val transportState = new GrpcWireMockServerTransportState(statsTraceContext);

        stream = new GrpcWireMockServerStream(asyncContext, transportState, statsTraceContext);
        serverTransportListener.streamCreated(stream, methodName, headers);

        stream.start();

        val source = new GrpcWireMockServerTransportSource(request.getInputStream());
        source.start();
    }

    @Override
    public void shutdown() {
        if (serverTransportListener != null) {
            SharedResourceHolder.release(GrpcUtil.TIMER_SERVICE, scheduledExecutorService);
            serverTransportListener.transportTerminated();
        }
    }

    @Override
    public void shutdownNow(Status reason) {
        if (this.stream != null) {
            this.stream.transportState().transportReportStatus(reason);
        }
    }

    @Override
    public ListenableFuture<SocketStats> getStats() {
        return null;
    }
}
