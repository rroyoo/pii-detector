package io.github.rroyoo.piidetector.poc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PocController {

    private static final Logger log = LoggerFactory.getLogger(PocController.class);
    private static final String NAME = "Ana Ejemplo";
    private static final String DNI = "12345678Z";
    private static final String CARD = "4242424242424242";
    private static final String EMAIL = "ana.ejemplo@example.com";
    private static final String IP = "203.0.113.42";

    @PostMapping("/poc/logs")
    public ResponseEntity<Map<String, String>> submitSyntheticLog() {
        log.atInfo()
                .addKeyValue("name", NAME)
                .addKeyValue("dni", DNI)
                .addKeyValue("credit_card", CARD)
                .log("Synthetic PII detection event");

        return ResponseEntity.accepted().body(Map.of("status", "logged", "data", "synthetic"));
    }

    @PostMapping("/poc/logs/unstructured")
    public ResponseEntity<Map<String, String>> submitUnstructuredLog() {
        log.info("Pago rechazado para el cliente {} con DNI {} usando la tarjeta {}, contacto: {} desde {}",
                NAME, DNI, CARD, EMAIL, IP);

        return ResponseEntity.accepted().body(Map.of("status", "logged", "data", "synthetic"));
    }
}
