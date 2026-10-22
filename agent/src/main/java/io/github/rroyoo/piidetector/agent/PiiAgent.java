package io.github.rroyoo.piidetector.agent;

import java.lang.instrument.Instrumentation;

/**
 * Java agent entry point. Captures application logs and ships them to the
 * sidecar over OTLP/HTTP (default http://localhost:4318/v1/logs).
 * Analysis only: the agent must never alter or mask log content.
 */
public final class PiiAgent {

    private PiiAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        String endpoint = System.getenv().getOrDefault("PII_SIDECAR_URL", "http://localhost:4318/v1/logs");
        OtlpLogExporter exporter = new OtlpLogExporter(endpoint, System.getProperty("otel.service.name",
                System.getenv().getOrDefault("OTEL_SERVICE_NAME", "unknown")));
        LogCapture.install(inst, exporter);
    }
}
