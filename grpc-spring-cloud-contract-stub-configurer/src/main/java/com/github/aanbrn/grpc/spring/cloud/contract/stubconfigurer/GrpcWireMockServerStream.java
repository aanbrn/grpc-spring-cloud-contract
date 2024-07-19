package com.github.aanbrn.grpc.spring.cloud.contract.stubconfigurer;

import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.internal.*;
import lombok.*;
import wiremock.javax.servlet.AsyncEvent;
import wiremock.javax.servlet.AsyncListener;
import wiremock.javax.servlet.ServletOutputStream;
import wiremock.javax.servlet.WriteListener;
import wiremock.javax.servlet.http.HttpServletResponse;
import wiremock.org.eclipse.jetty.http.HttpFields;
import wiremock.org.eclipse.jetty.server.AsyncContextState;
import wiremock.org.eclipse.jetty.server.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.grpc.internal.GrpcUtil.CONTENT_TYPE_KEY;
import static io.grpc.internal.GrpcUtil.DEFAULT_MAX_MESSAGE_SIZE;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static wiremock.org.apache.commons.io.IOUtils.closeQuietly;

class GrpcWireMockServerStream extends AbstractServerStream {

    static class GrpcWireMockServerTransportState extends TransportState {

        private final SerializingExecutor transportThreadExecutor =
                new SerializingExecutor(MoreExecutors.directExecutor());

        @Setter(AccessLevel.PRIVATE)
        private GrpcWireMockServerStream stream;

        GrpcWireMockServerTransportState(final StatsTraceContext statsTraceContext) {
            super(DEFAULT_MAX_MESSAGE_SIZE, statsTraceContext, new TransportTracer());
        }

        @Override
        public void runOnTransportThread(@NonNull final Runnable runnable) {
            transportThreadExecutor.execute(runnable);
        }

        @Override
        public void bytesRead(final int numBytes) {
        }

        @Override
        public void deframeFailed(@NonNull final Throwable cause) {
            if (stream != null) {
                stream.cancel(Status.fromThrowable(cause));
            }
        }
    }

    private static class ByteArrayWritableBuffer implements WritableBuffer {

        private final int capacity;

        private final byte[] bytes;

        private int offset;

        ByteArrayWritableBuffer(final int capacityHint) {
            this.capacity = min(max(4096, capacityHint), 1024 * 1024);
            this.bytes = new byte[this.capacity];
        }

        @Override
        public void write(byte[] src, int srcIndex, int length) {
            System.arraycopy(src, srcIndex, bytes, offset, length);
            offset += length;
        }

        @Override
        public void write(byte b) {
            bytes[offset++] = b;
        }

        @Override
        public int writableBytes() {
            return capacity - offset;
        }

        @Override
        public int readableBytes() {
            return offset;
        }

        @Override
        public void release() {
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private class GrpcWireMockServerStreamSink implements Sink, AsyncListener, WriteListener {

        private interface OutputTask {

            void run() throws IOException;
        }

        @NonNull
        private final ServletOutputStream output;

        private final HttpFields httpTrailers = new HttpFields();

        private final Queue<OutputTask> outputQueue = new ConcurrentLinkedQueue<>();

        private final AtomicBoolean outputting = new AtomicBoolean();

        private void start() {
            asyncContext.addListener(this);
            output.setWriteListener(this);
        }

        @Override
        public void writeHeaders(final Metadata headers, final boolean flush) {
            headers.discardAll(CONTENT_TYPE_KEY);
            headers.discardAll(GrpcUtil.TE_HEADER);
            headers.discardAll(GrpcUtil.USER_AGENT_KEY);

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(GrpcUtil.CONTENT_TYPE_GRPC);

            val serializedHeaders = TransportFrameUtil.toHttp2Headers(headers);
            for (int i = 0; i < serializedHeaders.length; i += 2) {
                response.addHeader(
                        new String(serializedHeaders[i], StandardCharsets.US_ASCII),
                        new String(serializedHeaders[i + 1], StandardCharsets.US_ASCII));
            }

            response.setTrailers(() -> httpTrailers);

            if (flush) {
                try {
                    execute(response::flushBuffer);
                } catch (IOException e) {
                    cancel(Status.fromThrowable(e));
                }
            }
        }

        @Override
        public void writeFrame(final WritableBuffer frame, final boolean flush, final int numMessages) {
            if (frame == null && !flush) {
                return;
            }

            try {
                if (frame != null) {
                    val numBytes = frame.readableBytes();
                    if (numBytes > 0) {
                        onSendingBytes(numBytes);
                    }

                    execute(() -> {
                        output.write(((ByteArrayWritableBuffer) frame).bytes, 0, numBytes);

                        transportState.runOnTransportThread(() -> transportState.onSentBytes(numBytes));
                    });
                }

                if (flush) {
                    execute(response::flushBuffer);
                }
            } catch (IOException e) {
                cancel(Status.fromThrowable(e));
            }
        }

        @Override
        public void writeTrailers(@NonNull final Metadata trailers, final boolean headersSent, final Status status) {
            if (headersSent) {
                val serializedHeaders = TransportFrameUtil.toHttp2Headers(trailers);
                for (int i = 0; i < serializedHeaders.length; i += 2) {
                    val key = new String(serializedHeaders[i], StandardCharsets.US_ASCII);
                    val newValue = new String(serializedHeaders[i + 1], StandardCharsets.US_ASCII);
                    httpTrailers.add(key, newValue);
                }
            } else {
                writeHeaders(trailers, false);
            }

            try {
                execute(() -> {
                    transportState.complete();

                    closeQuietly(output);

                    asyncContext.complete();
                });
            } catch (IOException ignored) {
            }
        }

        @Override
        public void cancel(@NonNull final Status status) {
            transportState.runOnTransportThread(() -> transportState.transportReportStatus(status));

            if (response.isCommitted() || status == Status.DEADLINE_EXCEEDED) {
                return;
            }

            close(Status.CANCELLED.withCause(status.asRuntimeException()), new Metadata());

            try {
                execute(() -> {
                    closeQuietly(output);

                    asyncContext.complete();
                });
            } catch (IOException ignored) {
            }
        }

        @Override
        public void onWritePossible() throws IOException {
            drainOutputQueue();
        }

        @Override
        public void onError(@NonNull final Throwable t) {
            closeQuietly(output);

            cancel(Status.fromThrowable(t));
        }

        @Override
        public void onError(@NonNull final AsyncEvent event) {
            if (!response.isCommitted()) {
                cancel(Status.fromThrowable(event.getThrowable()));
            } else {
                transportState.runOnTransportThread(() -> transportState.transportReportStatus(
                        Status.fromThrowable(event.getThrowable())));
            }
        }

        @Override
        public void onTimeout(final AsyncEvent event) {
            if (!response.isCommitted()) {
                cancel(Status.DEADLINE_EXCEEDED);
            } else {
                transportState.runOnTransportThread(() -> transportState.transportReportStatus(
                        Status.DEADLINE_EXCEEDED));
            }
        }

        @Override
        public void onStartAsync(final AsyncEvent event) {
        }

        @Override
        public void onComplete(final AsyncEvent event) {
        }

        private void execute(@NonNull final OutputTask outputTask) throws IOException {
            outputQueue.add(outputTask);
            drainOutputQueue();
        }

        private void drainOutputQueue() throws IOException {
            if (output.isReady() && outputting.compareAndSet(false, true)) {
                try {
                    while (output.isReady()) {
                        val outputTask = outputQueue.poll();
                        if (outputTask != null) {
                            outputTask.run();
                        } else {
                            break;
                        }
                    }
                } finally {
                    outputting.set(false);
                }
            }
        }
    }

    private final AsyncContextState asyncContext;

    private final Response response;

    private final GrpcWireMockServerTransportState transportState;

    private final GrpcWireMockServerStreamSink sink;

    GrpcWireMockServerStream(
            @NonNull final AsyncContextState asyncContext,
            @NonNull final GrpcWireMockServerTransportState transportState,
            @NonNull final StatsTraceContext statsTraceContext) throws IOException {
        super(ByteArrayWritableBuffer::new, statsTraceContext);

        this.asyncContext = asyncContext;
        this.response = (Response) asyncContext.getResponse();
        this.transportState = transportState;
        this.transportState.setStream(this);

        this.sink = new GrpcWireMockServerStreamSink(this.response.getOutputStream());
    }

    void start() {
        sink.start();

        transportState.onStreamAllocated();
    }

    @Override
    public int streamId() {
        return -1;
    }

    @Override
    protected GrpcWireMockServerTransportState transportState() {
        return transportState;
    }

    @Override
    protected Sink abstractServerStreamSink() {
        return sink;
    }
}
