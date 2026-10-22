package io.github.rroyoo.piidetector.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Sends log lines as OTLP/HTTP JSON, asynchronously and fail-safe. */
public final class OtlpLogExporter {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
    private final URI endpoint;
    private final String serviceName;

    public OtlpLogExporter(String endpoint, String serviceName) {
        this.endpoint = URI.create(endpoint);
        this.serviceName = serviceName;
    }

    public void export(String message) {
        String json = "{\"resourceLogs\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\",\"value\":{\"stringValue\":\""
                + escape(serviceName) + "\"}}]},\"scopeLogs\":[{\"logRecords\":[{\"body\":{\"stringValue\":\""
                + escape(message) + "\"}}]}]}]}";
        HttpRequest req = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        client.sendAsync(req, HttpResponse.BodyHandlers.discarding()).exceptionally(e -> null);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
