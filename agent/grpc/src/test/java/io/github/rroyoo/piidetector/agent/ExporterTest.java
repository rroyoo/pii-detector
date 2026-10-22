package io.github.rroyoo.piidetector.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerServiceDefinition;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.ServerCalls;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ExporterTest {

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

    @Test
    void grpcExporterSendsOtlpRequest() throws Exception {
        BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
        MethodDescriptor<byte[], byte[]> method = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("opentelemetry.proto.collector.logs.v1.LogsService/Export")
                .setRequestMarshaller(BYTES)
                .setResponseMarshaller(BYTES)
                .build();
        ServerServiceDefinition service = ServerServiceDefinition
                .builder("opentelemetry.proto.collector.logs.v1.LogsService")
                .addMethod(method, ServerCalls.asyncUnaryCall((req, obs) -> {
                    received.add(req);
                    obs.onNext(new byte[0]);
                    obs.onCompleted();
                }))
                .build();
        Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(service).build().start();
        try {
            OtlpGrpcLogExporter exporter = new OtlpGrpcLogExporter("127.0.0.1:" + server.getPort(), "s");
            exporter.export("m");
            byte[] body = received.poll(5, TimeUnit.SECONDS);
            assertNotNull(body, "server did not receive the export");
            assertArrayEquals(OtlpProtobuf.exportLogsRequest("s", "m"), body);
        } finally {
            server.shutdownNow();
        }
    }

    @Test
    void grpcExporterNeverThrowsWhenSidecarIsDown() {
        OtlpGrpcLogExporter exporter = new OtlpGrpcLogExporter("127.0.0.1:1", "s");
        assertDoesNotThrow(() -> exporter.export("m"));
    }

    @Test
    void grpcIsSelectedWhenAvailable() {
        assertInstanceOf(OtlpGrpcLogExporter.class,
                PiiAgent.createExporter(Map.of("PII_EXPORT_PROTOCOL", "GRPC"), "s"));
    }
}
