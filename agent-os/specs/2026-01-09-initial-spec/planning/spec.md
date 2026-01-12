# Specification: Initial Feature — 2026-01-09

Date: 2026-01-09
Spec path: agent-os/specs/2026-01-09-initial-spec/planning/spec.md
Based on: agent-os/specs/2026-01-09-initial-spec/spec.md and planning/requirements.md

## 1. Purpose
Provide a production-ready backend that reliably ingests telemetry and simulation payloads from hobbyist microcontrollers, normalizes variants of incoming data, persists time-series to Redis Streams, and exposes stable UI-facing APIs (REST & WebSocket) for a Kotlin Multiplatform UI. The system will support an export/archival path to PostgreSQL and include enterprise features (SSO/RBAC) for early adoption.

## 2. Scope
In scope (MVP+):
- Robust, lenient ingestion endpoints for epidemic and temperature payloads with normalization/transformation into canonical DTOs.
- Persist raw and normalized messages to Redis Streams and maintain indices for runs and devices.
- UI-facing REST endpoints for runs, timelines and summaries, and temperature device series and summaries, plus optional WebSocket live feeds.
- Integration and compatibility with the Kotlin Multiplatform UI at /home/{user}/IdeaProjects/Statistics-UI.
- Local deployment targeting lightweight Kubernetes (minikube/k3d) with docker-compose fallback.
- Archival/export pipeline to move or copy data from Redis to PostgreSQL for long-term retention and analytics.
- Enterprise scaffolding for SSO and RBAC with configuration switches (not full enterprise rollout).

Out of scope:
- Full ClickHouse analytics migration and massive-scale optimizations (deferred).
- Production multi-tenant billing and quota controls.

## 3. Goals & Success Criteria
- Ingestion reliability: 99%+ of synthetic device messages persisted in Redis Streams during smoke tests.
- UI integration: Demo Kotlin Multiplatform UI can list runs and render timelines and temperature charts within 5s after stack startup.
- Deployment: A minikube/k3d manifest and scripts (and docker-compose) can bring up a working demo locally.
- Enterprise readiness: Basic SSO/RBAC flows integrated in CI/dev builds and documented.

## 4. User Stories
- As a home lab hobbyist, I can deploy the stack locally and see my device data visualized in the Kotlin Multiplatform UI.
- As a developer, I can send epidemic or temperature payloads (per device contracts) and observe normalized entries in the Redis Streams.
- As an operator, I can archive historic data to PostgreSQL for ad-hoc analysis.
- As an admin, I can enable SSO and assign RBAC roles for users in larger deployments.

## 5. Functional Requirements
1. Ingestion endpoints
   - POST /v1/api/stats/collect/epidemic
     - Accepts JSON per RPI_Epidemic_Contract but must tolerate minor variants; a normalization layer will map/convert fields to canonical DTOs.
     - Respond 204 on success, 400 with details on invalid messages when unrecoverable.
   - POST /v1/api/stats/collect/temperature
     - Accepts JSON per RPI_Temperature_Contract; tolerate missing humidity fields and normalize timestamps.
2. Persistence
   - Raw payloads and normalized DTOs must be appended to Redis Streams (keys: epidemic:device:{deviceId}:run:{runId}, temperature:device:{deviceId}).
   - Maintain indices (sets/hashes) for runs and devices (epidemic:runs, temperature:devices) to enable list endpoints.
3. UI-facing APIs (per .docs contracts)
   - GET /api/v1/stats/expose/epidemic/runs
   - GET /api/v1/stats/expose/epidemic/device/{deviceId}/run/{runId}
   - GET /api/v1/stats/expose/epidemic/device/{deviceId}/run/{runId}/summary
   - GET /api/temperature/devices
   - GET /api/temperature/devices/{deviceId}?from=&to=&resolution=
   - GET /api/temperature/devices/{deviceId}/summary
   - WebSocket endpoints: /api/runs/{runId}/live and /api/temperature/{deviceId}/live (optional)
4. Archival
   - Background worker or scheduled job that reads from Redis Streams and writes/aggregates into PostgreSQL for long-term retention.
5. Enterprise features
   - Configurable SSO provider integration points (OIDC) and RBAC enforcement on UI-facing endpoints. Default: disabled for local dev.
6. Deployment
   - Kubernetes manifests (minikube/k3d) and convenience scripts; a docker-compose file for simpler dev runs.
7. Observability
   - Expose Prometheus metrics and basic Grafana dashboards for ingestion/processing rate, errors, and stream sizes.

## 6. Non-Functional Requirements
- Expected throughput: tens of devices sending messages at ~1 message/min to 1 message/30s; system must handle spikes gracefully.
- Latency: UI queries for recent data should return within 500–1000 ms when reading from Redis; live updates delivered over WebSocket within 1s of ingestion.
- Retention: Redis Streams configured with configurable TTL; archival copies stored in PostgreSQL daily.
- Security: Enterprise features available; for MVP local dev, security can be relaxed but documented.

## 7. Data Models
Follow the Kotlin DTOs in `.docs` (EpidemicPoint, EpidemicRun, TemperatureDto, TemperaturePoint). The ingestion normalization layer maps incoming payloads into these canonical models.

## 8. Implementation Notes
- Language & Framework: Kotlin + Ktor for backend; share DTOs with UI using Kotlin Multiplatform if possible.
- Storage: Redis Streams for ingestion and fast reads; PostgreSQL for archival/analytics.
- Normalization: Implement a small adapter layer per contract that can apply field renames, type coercions, timestamp parsing, and defaulting.
- Background jobs: Use lightweight coroutines/scheduled tasks to perform archival and aggregated computations.
- Testing: Unit tests for normalization, contract compliance tests (positive/negative cases), integration tests for endpoints, and smoke tests with demo UI.

## 9. Acceptance Criteria
- Endpoints accept representative device payloads (test vectors from /home/{user}/IdeaProjects/PicoProjectsPythong) and persist entries to Redis Streams.
- Demo UI at /home/{user}/IdeaProjects/Statistics-UI can consume the UI endpoints and display runs and temperature charts.
- Local Kubernetes manifests successfully start the stack in minikube (or docker-compose for fallback) and the demo flow completes end-to-end.
- Basic SSO/RBAC flow documented and togglable; unit tests and integration tests covering ingestion and key endpoints exist.

## 10. Deliverables
- Backend implementation (Ktor) with ingestion endpoints, normalization, Redis persistence, REST and WebSocket UI endpoints.
- Archival worker to export data to PostgreSQL.
- Kubernetes manifests (minikube/k3d) and docker-compose for local deployment.
- Integration test suite and example device payloads.
- Documentation: API contracts (.docs), deployment README, and admin guide for SSO/RBAC.

## 11. Dependencies & Reuse
- Reuse the existing Kotlin Multiplatform UI at: /home/{user}/IdeaProjects/Statistics-UI
- Use device payload examples from: /home/{user}/IdeaProjects/PicoProjectsPythong
- Follow code patterns in: /home/{user}/IdeaProjects/StatisticsService/src

## 12. Open Questions
- Exact SSO provider(s) to support first (Keycloak / Okta / generic OIDC)? Default: Keycloak recommended for self-hosted workflows.
- Archival schema and aggregation granularity in PostgreSQL (per-run vs per-minute buckets).

---

Author: spec-writer agent
