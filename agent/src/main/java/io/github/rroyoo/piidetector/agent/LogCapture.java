package io.github.rroyoo.piidetector.agent;

import java.lang.instrument.Instrumentation;

/**
 * Hooks the application's logging framework (Logback, the Spring Boot default)
 * so every event is forwarded to the exporter. TODO: implement via ByteBuddy
 * advice on ch.qos.logback.classic.Logger#callAppenders.
 */
final class LogCapture {

    private LogCapture() {
    }

    static void install(Instrumentation inst, OtlpLogExporter exporter) {
        // intentionally empty: scaffold
    }
}
