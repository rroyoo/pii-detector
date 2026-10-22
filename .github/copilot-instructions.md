# Copilot instructions

# Copilot instructions

PII/GDPR log analysis. Detection only: never mask, rewrite, or persist log content.

## Architecture
App (+ Java agent in `agent/`) → OTLP/HTTP → Go sidecar (`sidecar/`) → Presidio Analyzer (same container) → Prometheus metrics.
Flow in the sidecar: `internal/otlp` (receiver) → `internal/pipeline` → `internal/presidio` (adapter behind the `Analyzer` interface) → `internal/metrics`.
Integration target: `../mockjdbc-foundation/mockjdbc-spring-users` (Spring Boot 3.5, Java 17, Maven).

## Commands
- Sidecar: `cd sidecar && go build ./... && go test ./...`; single test: `go test ./internal/pipeline -run TestName`
- Agent: `cd agent && mvn package`; single test: `mvn test -Dtest=ClassName#method`

## Conventions
- Metric labels may only be service name and entity type — never PII values or log text.
- Keep Presidio access behind the `presidio.Analyzer` interface so it can be faked in tests.
- The agent must be fail-safe: exporting is async and must never break or slow the host application.
- Sidecar uses the Go standard library only unless a dependency is clearly needed.
