package io.github.rroyoo.piidetector.agent;

import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/**
 * Hooks Logback events and forwards their message and structured key-value fields.
 */
public final class LogCapture {

    private static final AtomicBoolean warned = new AtomicBoolean();
    private static volatile OtlpLogExporter exporter;

    private LogCapture() {
    }

    static void install(Instrumentation inst, OtlpLogExporter exporter) {
        LogCapture.exporter = exporter;
        new AgentBuilder.Default()
                .ignore(nameStartsWith("net.bytebuddy.")
                        .or(nameStartsWith("io.github.rroyoo.piidetector.agent.")))
                .type(named("ch.qos.logback.classic.Logger"))
                .transform((builder, type, classLoader, module, protectionDomain) ->
                        builder.visit(Advice.to(LogAdvice.class)
                                .on(named("callAppenders").and(takesArguments(1)))))
                .installOn(inst);
    }

    public static void exportEvent(Object event) {
        try {
            StringBuilder message = new StringBuilder();
            Object formattedMessage = event.getClass().getMethod("getFormattedMessage").invoke(event);
            appendField(message, "message", formattedMessage);

            Object keyValues = event.getClass().getMethod("getKeyValuePairs").invoke(event);
            if (keyValues instanceof List<?> pairs) {
                for (Object pair : pairs) {
                    String key = String.valueOf(pair.getClass().getField("key").get(pair));
                    Object value = pair.getClass().getField("value").get(pair);
                    appendField(message, key, value);
                }
            }
            OtlpLogExporter currentExporter = exporter;
            if (currentExporter != null) {
                currentExporter.export(message.toString());
            }
        } catch (ReflectiveOperationException e) {
            if (warned.compareAndSet(false, true)) {
                System.err.println("PII detector agent could not read a Logback event: " + e.getMessage());
            }
        }
    }

    private static void appendField(StringBuilder target, String key, Object value) {
        if (target.length() > 0) {
            target.append(' ');
        }
        target.append(key).append('=').append(value);
    }

    public static class LogAdvice {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void onEnter(@Advice.Argument(0) Object event) {
            exportEvent(event);
        }
    }
}
