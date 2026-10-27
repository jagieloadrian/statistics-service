# Research: Universal Statistics Platform — Build vs Buy + Technical Direction

Input for `/speckit-plan`. Non-binding: the plan confirms or overrides each
decision. Spec: [spec.md](./spec.md).

## 1. Current state (both repos, 2026-10-27)

- **StatisticsService**: Ktor + Redis Streams. Generic `POST /stats/collect/{pluginId}`
  and `/stats/expose/{pluginId}/**`, but every domain is a hand-written plugin
  module (DTOs, validation, collector, exposer with its own aggregation and
  routes). `StatsRepository` stores flat string maps; all aggregation
  (resolution buckets, summaries) is in-memory per plugin. No auth.
- **Statistics-UI**: KMP Compose (web wasm/js + desktop). Per-domain DTOs,
  clients, `ApiUrls`, screens and tiles hardcoded (`HomeScreen` tiles,
  `TemperatureConfigChart`, `EpidemicConfigChart`). Account screen is a stub.
- **Bug found**: UI `getEpidemicDetailsUrl` = `/epidemic/runs/{d}/run/{r}`,
  backend serves `/epidemic/device/{d}/run/{r}` — run details 404 today.

Conclusion: the plugin contract made *routing* generic but left *data model,
aggregation and screens* per-domain. That is exactly what the respec removes:
domain = configuration (source + mapping + dataset + dashboard), not code.

## 2. Build vs buy

| Option | Covers | Misses / cost |
|--------|--------|---------------|
| **A. Grafana + time-series DB + collector** (Telegraf / Vector / Grafana Alloy → PostgreSQL/TimescaleDB or InfluxDB) | Dashboards, variables, all widget types, users/teams/orgs, roles, sharing, provisioning as code. Collectors already do HTTP push listener, HTTP pull, Kafka consumer, MQTT, OTLP/gRPC. Grafana pulls REST directly via Infinity plugin; community Kafka datasource exists. | Not your own frontend (KMP app goes away). Ingestion config is collector config files, not a UI form. ~0 lines of code for ~90–95% of the spec. |
| **B. ThingsBoard CE** | IoT-native: devices, HTTP/MQTT/CoAP telemetry, rule engine, widget library, dashboards, customers/users. Fits the RPi devices well. | Heavy (Java + Postgres/Cassandra), opinionated device model; inbound integrations (incl. Kafka) are mostly in the paid PE edition. |
| **C. Metabase / Superset / Redash** | BI dashboards + users over SQL databases. | No ingestion at all; built for analytics queries, not telemetry streams. Not a fit alone. |
| **D. Hybrid: own backend + Grafana as frontend** | Own generic ingestion/mapping/query backend (this spec minus US3/US4 UI work); Grafana renders it via its PostgreSQL datasource or the query API. | Two "frontends" to reason about if KMP UI is kept too. |
| **E. Own build (this spec)** | Full control, own KMP frontend, learning value, exactly the UX wanted. | Largest effort: dashboard editor, auth, connectors are all yours to build and maintain. |

**Recommendation**

- Goal = *working tool fast* → **A**. Devices stay unchanged: a collector's
  HTTP listener can serve today's `/api/v1/stats/collect/temperature` path and
  parse the same JSON. Epidemic `byType` becomes tags. Nothing to write but config.
- Goal = *own product / Kotlin+KMP project* → **E**, but copy Grafana's
  proven model (source → query → widget → dashboard + variables) instead of
  inventing one, and design the query API so Grafana can read it too (cheap
  escape hatch to **D** later).

The spec is written for **E** (it is what was asked: separate own backend and
frontend). Option A is the honest baseline to compare effort against.

## 3. Technical direction for E (to confirm in `/speckit-plan`)

**Storage — PostgreSQL, one DB for everything.**
Users, sources, datasets, dashboards are relational; records go in one table
`records(dataset_id, ts, dims jsonb, vals jsonb)` with index `(dataset_id, ts)`
+ GIN on `dims`. Buckets via `date_bin()` + `GROUP BY`, aggregations in SQL
(replaces in-memory bucketing in today's exposer services).
Ceiling: tens of millions of rows; upgrade path = TimescaleDB extension
(hypertable, continuous aggregates) on the same SQL. Redis Streams dropped —
it cannot do ad-hoc aggregate range queries and a relational store is needed
for the user panel anyway.

**Universal record.** Every channel converges on one shape:
`{dataset, ts, dims: {device: "RPI_Pico_2w_1", run: "5", type: "0"}, vals: {infected: 2}}`.
Mapping (FR-007) = path selection (`meta.deviceId`), rename, cast, constant
dims, and expand (`state.byType.*` → one record per key, key → `type` dim).

**Ingestion channels.** One coroutine per active source inside the backend.
- Push: `POST /api/v2/ingest/{sourceId}`; today's
  `/api/v1/stats/collect/{temperature|epidemic}` become aliases = two source
  ids (FR-023), so firmware never changes.
- Pull: delay loop + Ktor HttpClient, cursor/identity for de-dup.
- Kafka: `kafka-clients` consumer, commit offset only after DB write (FR-005).
- gRPC: one small proto (`Write(stream Record) returns (stream Ack)`), or
  accept OTLP if producers already use OpenTelemetry — decide when the first
  gRPC producer exists.

**Plugins — mostly don't.**
- Exposure plugins: gone. Generic query API + widget definitions replace every
  per-plugin route and DTO.
- Ingestion: prefer **out-of-process collectors** (any language, Telegraf /
  Vector / Redpanda Connect or a small own app like the Home Assistant
  project) talking push/Kafka/gRPC. Process isolation, no rebuild, no
  classloader issues — they are plugins in all but name.
- In-process SPI (`java.util.ServiceLoader`, jars in `plugins/`): rejected
  (spec FR-024, clarified 2026-10-27). Revisit only if a collector provably
  cannot run outside the platform.

**Organizations.** v1 = one implicit organization; every owned row carries
its organization reference from day one so multi-org (spec FR-020) is a
later additive change, not a migration of definitions or contracts.

**Query API.** `POST /api/v2/query` with JSON body (dataset, measures, agg,
filters, groupBy, from/to, bucket) → series; `GET /api/v2/datasets/{id}/dims/{dim}/values`
for pickers (FR-013). Versioned, documented in OpenAPI (Swagger already wired).

**Auth.** Platform-managed accounts (hashed passwords), Ktor sessions,
roles in DB. OIDC/SSO later.

**Frontend (KMP Compose kept).** One `WidgetRenderer(type)` instead of
per-domain screens; dashboard = JSON definition from backend; editors are
forms; grid = fixed 12 columns with width/height, no drag-and-drop in v1.
Chart lib: `charty` (already used). Risk: Compose-for-Web ergonomics for a
form-heavy editor and chart breadth — fallback is a TS frontend (e.g. ECharts)
behind the same API, possible because FR-014 keeps the contract client-agnostic.

**Delete at cutover.** `plugin/*` modules, `StatsRepositoryRedisImpl`,
`RedisClientProvider`, per-domain UI contracts in `.docs`; UI per-domain
DTOs, clients, `ApiUrls`, screens. **Keep**: Ktor skeleton, DI,
`GlobalExceptionHandler`, Swagger, device contracts `RPI_*_Contract.md` as
compatibility test fixtures.

## 4. Suggested delivery slices (one Speckit unit each)

1. Constitution v2.0.0 (principles I, II, IV rewritten: configuration over code,
   generic versioned contracts, auth in scope).
2. Backend core: dataset + push source + mapping + PostgreSQL store + query API;
   temperature via compat address (API-level tests only). — US1, US2 (part)
3. Epidemic via configuration (collection expansion, first/last/max-at). — US2
4. Frontend generic dashboard viewer, definitions imported from JSON; old
   screens removed. — US2, US3 (part)
5. Editors: sources (with mapping test), datasets, widgets, dashboards,
   variables. — US1, US3
6. User panel & auth. — US4
7. Cutover: no history migration (FR-023) — delete plugins + Redis code;
   Redis data volume left untouched as archive.
8. Pull HTTP → Kafka → gRPC, each only when a real producer exists; collector
   apps (e.g. Home Assistant) use whichever channel exists. — US5, US6
