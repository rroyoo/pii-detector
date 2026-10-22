package io.github.rroyoo.piidetector.agent;

import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.TimeUnit;

/** Sends log lines over OTLP/gRPC, asynchronously and fail-safe. */
public final class OtlpGrpcLogExporter implements LogExporter {

    private static final MethodDescriptor.Marshaller<byte[]> BYTES = new MethodDescriptor.Marshaller<>() {
        @Override
        public InputStream stream(byte[] value) {
            return new ByteArrayInputStream(value);
        }

        @Override
        public byte[] parse(InputStream stream) {
            try {
                return stream.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    };

    private static final MethodDescriptor<byte[], byte[]> EXPORT = MethodDescriptor.<byte[], byte[]>newBuilder()
            .setType(MethodDescriptor.MethodType.UNARY)
            .setFullMethodName("opentelemetry.proto.collector.logs.v1.LogsService/Export")
            .setRequestMarshaller(BYTES)
            .setResponseMarshaller(BYTES)
            .build();

    private static final StreamObserver<byte[]> IGNORE = new StreamObserver<>() {
        @Override
        public void onNext(byte[] value) {
        }

        @Override
        public void onError(Throwable t) {
        }

        @Override
        public void onCompleted() {
        }
    };

    private final ManagedChannel channel;
    private final String serviceName;

    /** Creates the channel lazily: no connection is attempted until the first export. */
    public OtlpGrpcLogExporter(String target, String serviceName) {
        this.channel = NettyChannelBuilder.forTarget(target).usePlaintext().build();
        this.serviceName = serviceName;
    }

    @Override
    public void export(String message) {
        try {
            ClientCalls.asyncUnaryCall(
                    channel.newCall(EXPORT, CallOptions.DEFAULT.withDeadlineAfter(2, TimeUnit.SECONDS)),
                    OtlpProtobuf.exportLogsRequest(serviceName, message), IGNORE);
        } catch (Throwable ignored) {
            // exporting must never affect the host application
        }
    }
}
