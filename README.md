
# Searchable Log Aggregator — GitHub Codespaces Edition (Java)

**Time budget:** ~6–8 hours over 2–3 days  
**Language:** Java (Spring Boot)  
**Runtime:** GitHub Codespaces via devcontainer

## Goal
Build a small service that ingests NDJSON logs, stores them, provides search & aggregation APIs, and detects simple rate anomalies. Focus on clean code, tests, observability, and developer experience in Codespaces.

## Functional Requirements
1. **Ingestion** (`POST /ingest`): Accept NDJSON (one JSON per line) with fields:
   ```json
   {"ts":"2025-07-01T12:31:05Z","app":"api-gateway","level":"INFO|WARN|ERROR","message":"...","attrs":{"userId":"U123","region":"eu-west-1"}}
   ```
   Persist to H2 (file mode) using JPA.
2. **Search** (`GET /search?app=&level=&q=&from=&to=&limit=&offset=`): Filters + pagination.
3. **Aggregations**
   - `GET /aggregations/top-apps?from=&to=&k=`: Top‑K apps by log count.
   - `GET /aggregations/error-rate?app=&window=5m`: Error counts per time bucket.
4. **Anomaly Detection** (`GET /anomalies?app=&from=&to=`): Flag rate spikes when current bucket’s count exceeds `μ + α·σ` (configurable, default `α=3.0`).

## Non‑Functional Requirements
- **Codespaces-first**: Use the included devcontainer.
- **Make targets**: `make bootstrap`, `make test`, `make run`, `make generate`.
- **Observability**: Structured logging, `GET /healthz`, minimal metric (rate spike counter).
- **Config**: `.env` or environment variables: `ALPHA`, `WINDOW_SEC`, `DB_FILE`, `LOG_LEVEL`.
- **Quality**: Unit tests for parsing/search/anomaly logic.

## Getting Started (Codespaces)
1. Open this repository in GitHub Codespaces.
2. After the devcontainer builds, run:
   ```bash
   make bootstrap
   make generate     # creates data/logs.ndjson
   make run          # starts API on http://localhost:8080
   ```
3. Ingest sample logs:
   ```bash
   curl -X POST --data-binary @data/logs.ndjson http://localhost:8080/ingest -H 'Content-Type: application/x-ndjson'
   ```
4. Try health check:
   ```bash
   curl http://localhost:8080/healthz
   ```
5. Try aggregation endpoints:
   ```bash
   # Get top 5 apps by log count
   curl 'http://localhost:8080/aggregations/top-apps?k=5'
   
   # Get top 3 apps within a time range
   curl 'http://localhost:8080/aggregations/top-apps?from=2025-07-01T00:00:00Z&to=2025-07-01T01:00:00Z&k=3'
   
   # Get error rate for an app with 5-minute windows (default)
   curl 'http://localhost:8080/aggregations/error-rate?app=billing&window=5m'
   
   # Get error rate with 1-hour windows
   curl 'http://localhost:8080/aggregations/error-rate?app=search&window=1h'
   ```

## Deliverables
- Source code + tests
- README updates with curl examples
- Multiple small commits
- (Optional) OpenAPI schema

## Notes
- The service stubs are present with TODOs. Implement endpoints, repository queries, and anomaly math.
- H2 DB file lives under `./data/logdb` (configurable).
- Provide simple error handling and meaningful 4xx/5xx responses.

Good luck!
