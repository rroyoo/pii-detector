package io.github.rroyoo.piidetector.agent;

import java.lang.instrument.Instrumentation;
import java.util.Map;

/**
 * Java agent entry point. Captures application logs and ships them to the sidecar over
 * OTLP/HTTP (default) or OTLP/gRPC, selected with {@code PII_EXPORT_PROTOCOL=http|grpc}.
 * The gRPC exporter only exists in the {@code -grpc} agent JAR.
 * Analysis only: the agent must never alter or mask log content, nor break the host application.
 */
public final class PiiAgent {

    static final String DEFAULT_HTTP_URL = "http://localhost:4318/v1/logs";
    static final String DEFAULT_GRPC_ADDR = "localhost:4317";
    private static final String GRPC_EXPORTER = "io.github.rroyoo.piidetector.agent.OtlpGrpcLogExporter";

    private PiiAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        try {
            Map<String, String> env = System.getenv();
            String service = System.getProperty("otel.service.name", env.getOrDefault("OTEL_SERVICE_NAME", "unknown"));
            LogCapture.install(inst, createExporter(env, service));
        } catch (Throwable t) {
            System.err.println("[pii-agent] disabled: " + t);
        }
    }

    static LogExporter createExporter(Map<String, String> env, String service) {
        String protocol = env.getOrDefault("PII_EXPORT_PROTOCOL", "http").trim().toLowerCase();
        if (protocol.equals("grpc")) {
            LogExporter grpc = grpcExporter(env.getOrDefault("PII_SIDECAR_GRPC_ADDR", DEFAULT_GRPC_ADDR), service);
            if (grpc != null) {
                return grpc;
            }
            System.err.println("[pii-agent] gRPC exporter not available in this JAR (use the -grpc agent), using http");
        } else if (!protocol.equals("http")) {
            System.err.println("[pii-agent] unknown PII_EXPORT_PROTOCOL '" + protocol + "', using http");
        }
        return new OtlpHttpLogExporter(env.getOrDefault("PII_SIDECAR_URL", DEFAULT_HTTP_URL), service);
    }

    private static LogExporter grpcExporter(String target, String service) {
        try {
            return (LogExporter) Class.forName(GRPC_EXPORTER)
                    .getConstructor(String.class, String.class).newInstance(target, service);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        } catch (ReflectiveOperationException e) {
            System.err.println("[pii-agent] could not create gRPC exporter: " + e);
            return null;
        }
    }
}
