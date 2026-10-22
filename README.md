# pii-detector

Log analysis system that detects PII leaks (GDPR). **Detection only — never masks or modifies logs.**

## Architecture
- `agent/` — Java agent (Maven, Java 17) that instruments Logback, captures message and structured key-value fields, and sends them asynchronously to the sidecar via OTLP/HTTP.
- `sidecar/` — Go process receiving OTLP logs (`:4318/v1/logs`), forwarding text through an adapter to Presidio (`PRESIDIO_URL`, default `http://localhost:3000`; `PRESIDIO_LANGUAGE`, default `en`; `PRESIDIO_MIN_SCORE`, default `0.4`, discards low-confidence findings), and exposing findings as Prometheus metrics (`:9464/metrics`).
- `analyzer/` — Presidio Analyzer image with English and Spanish NLP models and Spanish recognizers (`ES_NIF`, `ES_NIE`). Use it with `PRESIDIO_LANGUAGE=es`.
- `poc/` — standalone Spring Boot demo that emits synthetic structured and unstructured PII logs through the Java agent.
- `deploy/` — example pod with the app, sidecar, and Presidio Analyzer.

Integration target: `/home/rroyo/IdeaProjects/mockjdbc-foundation/mockjdbc-spring-users`.

## Commands
```bash
cd sidecar && go build ./... && go test ./...
cd agent && mvn package
cd poc && mvn test
```

## Local PoC

The standalone Spring Boot demo logs synthetic values (never use real personal data) and the Java agent
sends them to the sidecar as OTLP/HTTP:

```bash
cd poc && docker compose up --build -d
curl -X POST http://localhost:8080/poc/logs               # structured key-value log
curl -X POST http://localhost:8080/poc/logs/unstructured  # free-text Spanish sentence
curl http://localhost:9464/metrics
docker compose logs app
```

The PoC runs the demo app, Go sidecar, and Presidio Analyzer (`analyzer/`, Spanish) as separate containers.
The first build downloads the spaCy models and takes several minutes. If the analyzer stays `unhealthy`
after startup, run `docker compose restart analyzer`.
