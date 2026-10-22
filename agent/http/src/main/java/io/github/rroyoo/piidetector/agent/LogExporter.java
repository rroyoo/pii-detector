package io.github.rroyoo.piidetector.agent;

/** Ships one captured log line to the sidecar. Implementations must be asynchronous and never throw. */
public interface LogExporter {

    void export(String message);
}
