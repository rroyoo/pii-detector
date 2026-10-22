package io.github.rroyoo.piidetector.agent;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiAgentTest {

    @Test
    void defaultsToHttp() {
        assertInstanceOf(OtlpHttpLogExporter.class, PiiAgent.createExporter(Map.of(), "s"));
    }

    @Test
    void unknownProtocolFallsBackToHttp() {
        assertInstanceOf(OtlpHttpLogExporter.class,
                PiiAgent.createExporter(Map.of("PII_EXPORT_PROTOCOL", "bogus"), "s"));
    }

    @Test
    void grpcRequestedWithoutGrpcClassesFallsBackToHttp() {
        assertInstanceOf(OtlpHttpLogExporter.class,
                PiiAgent.createExporter(Map.of("PII_EXPORT_PROTOCOL", "grpc"), "s"));
    }
}
