# pii-detector

Log analysis system that detects PII leaks (GDPR). **Detection only — never masks or modifies logs.**

## Architecture
- `agent/` — Java agent (Maven, Java 17) that captures app logs and sends them to the sidecar via OTLP/HTTP.
- `sidecar/` — Go process receiving OTLP logs (`:4318/v1/logs`), forwarding text through an adapter to Presidio
  (same container, `PRESIDIO_URL`, default `http://localhost:5002`), and exposing findings as Prometheus metrics (`:9464/metrics`).
- `deploy/` — example pod with the app + sidecar.

Integration target: `/home/rroyo/IdeaProjects/mockjdbc-foundation/mockjdbc-spring-users`.

## Commands
```bash
cd sidecar && go build ./... && go test ./...
cd agent && mvn package
```
