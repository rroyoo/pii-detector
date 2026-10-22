package io.github.rroyoo.piidetector.agent;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OtlpProtobufTest {

    @Test
    void encodesExportLogsRequest() {
        byte[] expected = concat(
                new byte[] {0x0a, 0x20, 0x0a, 0x15, 0x0a, 0x13, 0x0a, 0x0c},
                "service.name".getBytes(StandardCharsets.UTF_8),
                new byte[] {0x12, 0x03, 0x0a, 0x01, 's', 0x12, 0x07, 0x12, 0x05, 0x2a, 0x03, 0x0a, 0x01, 'm'});

        assertArrayEquals(expected, OtlpProtobuf.exportLogsRequest("s", "m"));
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] p : parts) {
            total += p.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, pos, p.length);
            pos += p.length;
        }
        return out;
    }
}
