# Copilot instructions

# Copilot instructions

This repository detects possible PII in application logs for GDPR analysis. It is detection-only: never mask, rewrite, or persist log content.

## Build and test

- Sidecar build and tests: `cd sidecar && go build ./... && go test ./...`
- One Go test: `cd sidecar && go test ./internal/pipeline -run '^TestName$'`
- Build the Java agent: `cd agent && mvn package` (produces a shaded, executable `-javaagent` JAR)
- One Java test: `cd agent && mvn test -Dtest=ClassName#method`
- Build the structured-log demo: `cd poc && mvn test`
- Run the demo stack: `cd poc && docker compose up --build -d`

## Architecture

The intended flow is application → Java instrumentation agent (`agent/`) → OTLP/HTTP JSON → Go sidecar (`sidecar/`) → Presidio Analyzer → Prometheus metrics. The agent instruments Logback and exports the formatted message plus structured key-value fields asynchronously. The demo in `poc/` uses this agent; its controller only writes an application log and does not call the sidecar directly. The example deployment in `deploy/pod-example.yaml` places the app, sidecar, and Presidio Analyzer in one pod and connects them over localhost.

In the sidecar, `cmd/sidecar` wires the components together; `internal/otlp` accepts `POST /v1/logs` and extracts the service name and string log bodies; `internal/pipeline` passes each body to the analyzer and records counts; `internal/presidio` adapts the Presidio `/analyze` API; `internal/metrics` serves `/metrics` on a separate listener. Presidio defaults to `http://localhost:3000`; `PRESIDIO_URL`, `PRESIDIO_LANGUAGE`, `PRESIDIO_MIN_SCORE` (default `0.4`, sent to Presidio as `score_threshold`), `OTLP_HTTP_ADDR`, and `METRICS_ADDR` configure the sidecar. The stock Presidio image only has English models; `analyzer/` builds an image with English and Spanish models and recognizers (`ES_NIF`, `ES_NIE`), used by the PoC with `PRESIDIO_LANGUAGE=es`. The agent endpoint is configured with `PII_SIDECAR_URL`; service name comes from `otel.service.name`, then `OTEL_SERVICE_NAME`.

The reference demo application is `poc/` (Spring Boot 3.5, Java 17, Maven), which runs with the agent through `poc/compose.yaml`.

## Conventions

- Never mask, rewrite, or persist log text. Keep log content transient through the analysis path.
- Metric labels may contain only service name and entity type, never PII values or log text.
- Keep Presidio calls behind the `presidio.Analyzer` interface so pipeline behavior can be tested with a fake.
- Agent exporting must remain asynchronous and fail-safe; it must not break or slow the host application.
- The sidecar currently uses only the Go standard library; add dependencies only when clearly needed.
