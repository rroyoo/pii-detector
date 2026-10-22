# pii-detector

Log analysis system that detects personal data (PII) leaks in application logs, for GDPR compliance.
**Detection only: it never masks, rewrites, or stores log content.** Results are exposed as Prometheus
counters that carry only the service name and the entity type, never the detected values.

## How it works

```
Java app + agent ──OTLP/HTTP (JSON) or OTLP/gRPC──▶ Go sidecar ──HTTP──▶ Presidio Analyzer
                                            │
                                            └──▶ Prometheus metrics (:9464/metrics)
```

1. The **agent** hooks Logback inside the JVM, captures each log event (message and structured
   key-value fields) and sends it asynchronously to the sidecar over OTLP/HTTP or OTLP/gRPC. It is fail-safe: it never blocks or
   breaks the host application.
2. The **sidecar** receives the logs (OTLP/HTTP `POST /v1/logs` on `:4318`, OTLP/gRPC on `:4317`), asks Presidio to analyze the text, and counts
   the findings.
3. **Presidio Analyzer** detects entities such as `PERSON`, `CREDIT_CARD`, `EMAIL_ADDRESS`,
   `IP_ADDRESS` and the Spanish `ES_NIF` / `ES_NIE`.

## Repository layout

| Path | Description |
|---|---|
| `agent/` | Java 17 agent (Maven). Instruments Logback with Byte Buddy; shaded into a single JAR. |
| `sidecar/` | Go service (standard library only): OTLP receiver, pipeline, Presidio client, metrics. |
| `analyzer/` | Presidio Analyzer image with English and Spanish NLP models and recognizers. |
| `poc/` | Spring Boot demo app and Docker Compose stack to run everything locally. |
| `deploy/` | Example Kubernetes pod with the app, sidecar and Presidio. |

Inside `sidecar/internal/`: `otlp` (receiver) → `pipeline` → `presidio` (client behind the `Analyzer`
interface, so it can be faked in tests) → `metrics`.

## Prerequisites

- JDK 17 or newer and Maven 3.9+ (agent and PoC)
- Go 1.26 or newer (sidecar)
- Docker with Compose v2 (analyzer image and PoC)

## Building each module

### Agent

```bash
cd agent
mvn package
```

Produces two alternative agent JARs; pick one so HTTP users do not carry gRPC dependencies:

| JAR | Size | Transports |
|---|---|---|
| `agent/http/target/pii-detector-agent-http-1.0.0-SNAPSHOT-agent.jar` | ~4.5 MB | HTTP only |
| `agent/grpc/target/pii-detector-agent-grpc-1.0.0-SNAPSHOT-agent.jar` | ~20 MB | HTTP and gRPC |

If `PII_EXPORT_PROTOCOL=grpc` is set with the HTTP JAR, the agent warns and falls back to HTTP. Attach one to any
JVM application that uses Logback:

```bash
java -javaagent:/path/to/pii-detector-agent-http-1.0.0-SNAPSHOT-agent.jar -jar your-app.jar
```

In the PoC, `PII_EXPORT_PROTOCOL=grpc docker compose up -d --build` builds the gRPC flavor (`AGENT_FLAVOR` build arg).

Benchmark (loopback, fake Presidio, 20k messages): at 1000 msg/s both deliver 100%; at 5000 msg/s gRPC delivered 100% versus
~45% for HTTP (the agent drops when the queue is full, by design). Per-call latency is microseconds in both; with real
Presidio (~30 ms per analysis) the transport is not the bottleneck, so HTTP is the sensible default. Dependency scan (OSV):
no known vulnerabilities in the agent's 26 artifacts.

| Variable | Default | Description |
|---|---|---|
| `PII_EXPORT_PROTOCOL` | `http` | Transport to the sidecar: `http` or `grpc` |
| `PII_SIDECAR_URL` | `http://localhost:4318/v1/logs` | Sidecar OTLP/HTTP endpoint (protocol `http`) |
| `PII_SIDECAR_GRPC_ADDR` | `localhost:4317` | Sidecar OTLP/gRPC `host:port`, plaintext (protocol `grpc`) |
| `OTEL_SERVICE_NAME` | `unknown` | Service name used as the metric label |

### Sidecar

```bash
cd sidecar
go build ./...
go build -o sidecar ./cmd/sidecar   # binary
docker build -t pii-sidecar .       # distroless image
```

| Variable | Default | Description |
|---|---|---|
| `PRESIDIO_URL` | `http://localhost:3000` | Presidio Analyzer base URL |
| `PRESIDIO_LANGUAGE` | `en` | Language sent to Presidio (`en`, `es`) |
| `PRESIDIO_MIN_SCORE` | `0.4` | Minimum confidence (0 to 1); sent as `score_threshold` |
| `OTLP_PROTOCOLS` | `http,grpc` | Receivers to enable (`http`, `grpc`, or both) |
| `OTLP_HTTP_ADDR` | `:4318` | OTLP/HTTP listen address |
| `OTLP_GRPC_ADDR` | `:4317` | OTLP/gRPC listen address |
| `METRICS_ADDR` | `:9464` | Prometheus metrics listen address |

Presidio is always reached over its REST API; the Analyzer has no gRPC interface.

Metrics exposed at `/metrics`:

- `pii_findings_total{service, entity_type}`
- `pii_logs_analyzed_total`
- `pii_analysis_errors_total`

### Analyzer (Presidio)

```bash
docker build -t pii-analyzer analyzer/
docker run --rm -p 3000:3000 pii-analyzer
```

Builds on the official `presidio-analyzer` image and adds the spaCy models `en_core_web_lg` and
`es_core_news_md`, plus Spanish recognizers. Configuration lives in `analyzer/conf/`. The first build
downloads the models and takes several minutes; the image is about 1 GB. Presidio listens on port
**3000** (`/health`, `/analyze`).

### PoC application

```bash
cd poc
mvn package
```

Spring Boot 3.5 / Java 17. It is normally built through Docker Compose (see below), which also builds
the agent.

## Running the PoC

The PoC starts three containers: the demo app (running with the agent), the sidecar and the Spanish
Presidio analyzer. The app logs **synthetic** values only; never use real personal data.

```bash
cd poc
docker compose up --build -d
docker compose ps        # wait until the analyzer is "healthy"
```

Generate logs with PII:

```bash
# Structured log: key-value fields (name, DNI, card number)
curl -X POST http://localhost:8080/poc/logs

# Unstructured log: a free-text Spanish sentence with name, DNI, card, email and IP
curl -X POST http://localhost:8080/poc/logs/unstructured
```

Check the results:

```bash
curl http://localhost:9464/metrics
docker compose logs app sidecar
```

Expected entities after both calls include `ES_NIF`, `CREDIT_CARD`, `EMAIL_ADDRESS`, `IP_ADDRESS` and
`PERSON`. Counters are cumulative and the application's own startup logs are analyzed too, so counts
can be higher than the number of requests. `docker compose restart sidecar` resets them.

To send the logs over gRPC instead of HTTP:

```bash
PII_EXPORT_PROTOCOL=grpc docker compose up -d --force-recreate app
```

Stop everything with `docker compose down`.

Ports are bound to `127.0.0.1`: app `8080`, OTLP/gRPC `4317`, OTLP/HTTP `4318`, metrics `9464`. Presidio is not published; to
call it directly use the Compose network:

```bash
docker run --rm --network poc_default curlimages/curl -s -X POST http://analyzer:3000/analyze \
  -H 'Content-Type: application/json' \
  -d '{"text":"DNI 12345678Z","language":"es","score_threshold":0.4}'
```

### Troubleshooting

- The analyzer stays `unhealthy` after the first start: run `docker compose restart analyzer`.
- `ES_NIF` validates the control letter, so use valid values such as `12345678Z`.
- `PERSON` relies on a spaCy NER model and is not reliable on short or `key=value` text. Pattern-based
  entities (`ES_NIF`, `CREDIT_CARD`, `EMAIL_ADDRESS`, `IP_ADDRESS`) are deterministic.
- Presidio may report a low-confidence `URL` for the domain inside an email address.

## Running the tests

```bash
cd sidecar && go vet ./... && go test ./...     # sidecar unit tests
cd agent && mvn test                            # agent build and unit tests
cd poc && mvn test                              # PoC build and context tests
```

Current coverage: the sidecar tests the Presidio client (`score_threshold`) and both OTLP receivers
(HTTP and gRPC); the agent tests the protobuf encoding, the gRPC exporter against a local server and
the protocol selection. The PoC module compiles and runs `mvn test` but has no test classes yet. The end-to-end flow
is verified manually with the PoC steps above.

To run a single test: `go test ./internal/presidio -run TestName` or
`mvn test -Dtest=ClassName#method`.

## Kubernetes

`deploy/pod-example.yaml` shows the target topology: the application with the agent, the sidecar and
Presidio Analyzer in one pod, sharing `localhost`. Replace the image names with your own registry.

## Design rules

- Metric labels are limited to the service name and the entity type.
- Presidio access stays behind the `presidio.Analyzer` interface.
- The agent exports asynchronously and must never break or slow down the host application.
- The sidecar uses only the Go standard library unless a dependency is clearly needed.
