package io.github.rroyoo.piidetector.agent;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Minimal protobuf encoder for the OTLP ExportLogsServiceRequest (string bodies only). */
final class OtlpProtobuf {

    private OtlpProtobuf() {
    }

    static byte[] exportLogsRequest(String serviceName, String message) {
        byte[] attribute = message(1, stringKeyValue("service.name", serviceName));
        byte[] resource = attribute;
        byte[] record = message(5, anyString(message));
        byte[] scopeLogs = message(2, record);
        byte[] resourceLogs = concat(message(1, resource), message(2, scopeLogs));
        return message(1, resourceLogs);
    }

    private static byte[] stringKeyValue(String key, String value) {
        return concat(string(1, key), message(2, anyString(value)));
    }

    private static byte[] anyString(String value) {
        return string(1, value);
    }

    private static byte[] string(int field, String value) {
        return message(field, value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] message(int field, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(payload.length + 8);
        writeVarint(out, ((long) field << 3) | 2);
        writeVarint(out, payload.length);
        out.write(payload, 0, payload.length);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) value);
    }
}
