# Tasks — 2026-01-09-initial-spec

## Epic: Ingestion & Normalization
- [ ] Implement ingestion adapter layer to normalize incoming JSON to canonical DTOs (epidemic, temperature). `M`
  - [ ] Implement field mapping, type coercion, timestamp parsing and defaults. `S`
  - [ ] Add schema-driven validation with clear error responses for unrecoverable payloads. `S`
  - [ ] Add unit tests for normalization and validation (positive/negative vectors). `S`
- [ ] Implement POST /v1/api/stats/collect/epidemic endpoint using normalization adapter. `M`
  - [ ] Wire endpoint into Redis Streams append logic. `M`
- [ ] Implement POST /v1/api/stats/collect/temperature endpoint using normalization adapter. `M`
  - [ ] Wire endpoint into Redis Streams append logic. `M`

## Epic: Persistence & Indexing
- [ ] Design Redis Stream keys and entry format; implement append helpers. `S`
- [ ] Maintain indices for runs and devices (epidemic:runs, temperature:devices). `S`
- [ ] Implement configurable retention (TTL) for Redis Streams and documentation. `S`

## Epic: UI-facing APIs
- [ ] Implement GET /api/v1/stats/expose/epidemic/runs — list runs (supports pagination/filtering). `M`
- [ ] Implement GET /api/v1/stats/expose/epidemic/device/{deviceId}/run/{runId} — run timeline (reads from stream). `M`
- [ ] Implement GET /api/v1/stats/expose/epidemic/device/{deviceId}/run/{runId}/summary — run summary (aggregation). `S`
- [ ] Implement GET /api/temperature/devices and GET /api/temperature/devices/{deviceId}?from=&to=&resolution= — time-series endpoints with downsampling. `M`
- [ ] Implement GET /api/temperature/devices/{deviceId}/summary — aggregated stats for a time window. `S`
- [ ] Add optional WebSocket endpoints for live updates: /api/runs/{runId}/live and /api/temperature/{deviceId}/live. `S`

## Epic: Archival & Background Processing
- [ ] Implement background worker to export/aggregate data from Redis to PostgreSQL (daily batches). `M`
  - [ ] Define archival schema (per-run and per-minute buckets) and migration scripts. `S`
  - [ ] Add tests for archival correctness. `S`

## Epic: Enterprise & Security
- [ ] Implement SSO/OIDC integration scaffold (Keycloak recommended) and configuration. `M`
- [ ] Implement RBAC enforcement on UI-facing endpoints (roles: admin, viewer, device). `M`
- [ ] Add integration tests for RBAC flows and toggling SSO on/off. `S`

## Epic: Local Deployment & Dev Experience
- [ ] Provide Kubernetes manifests for minikube/k3d and convenience scripts to deploy the stack locally. `S`
- [ ] Maintain a docker-compose fallback for simpler dev environments. `S`
- [ ] Provide README with quickstart steps for local development and integration with the Kotlin Multiplatform UI at /home/{user}/IdeaProjects/Statistics-UI. `S`

## Epic: Demo UI Integration
- [ ] Coordinate with the Kotlin Multiplatform UI repo to ensure DTOs and endpoints are consumable. `S`
- [ ] Create small demo scenarios and test vectors using PicoProjectsPythong payload examples. `S`
- [ ] Add end-to-end integration test that exercises UI flows against a running local stack. `M`

## Epic: Testing & Observability
- [ ] Add unit tests for DTOs, normalization adapters, and persistence helpers. `S`
- [ ] Add integration tests for ingestion endpoints and key UI endpoints. `M`
- [ ] Expose Prometheus metrics (ingestion rate, error rate, stream lengths) and provide a Grafana dashboard example. `S`

## Epic: Exports & Tooling
- [ ] Implement CSV/JSON export endpoints for runs and time windows. `S`
- [ ] Provide small CLI tooling for demo data generation, import/export, and administrative tasks. `S`

## Notes
- Prioritize MVP tasks (ingestion, persistence, basic UI endpoints, demo integration) before advanced features (SSO full rollout, ClickHouse migration).
- Reuse DTO/test vectors from `/home/{user}/IdeaProjects/PicoProjectsPythong` and UI integration at `/home/{user}/IdeaProjects/Statistics-UI`.

