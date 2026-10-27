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

- Goal = *working tool fast* → **A**. Point the RPi at a collector's HTTP
  listener (same JSON); epidemic `byType` becomes tags. Nothing to write but config.
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
- Push: `POST /api/v2/ingest/{sourceId}`; RPi devices are reprogrammed to it
  (FR-023), payload shape kept via mapping (SC-003) or switched to the native
  record format (FR-025).
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

**Delete up front.** `plugin/*` modules, the v1 API routes,
`StatsRepositoryRedisImpl`, `RedisClientProvider`, all `.docs/*_Contract.md`
(moved to `.docs/archive/`); UI per-domain DTOs, clients, `ApiUrls`,
screens. **Keep**: Ktor skeleton, DI, `GlobalExceptionHandler`, Swagger;
sample payloads from `RPI_*_Contract.md` reused as test fixtures.

## 4. Suggested delivery slices (one Speckit unit each)

1. Constitution v2.x (done) + delete the old API, plugins, Redis code and
   domain screens in both repos.
2. Backend core: dataset + push source + mapping + PostgreSQL store + query API;
   temperature as first definition (API-level tests only). — US1, US2 (part)
3. Epidemic via configuration (collection expansion, first/last/max-at). — US2
4. Frontend generic dashboard viewer, definitions imported from JSON; old
   screens removed. — US2, US3 (part)
5. Editors: sources (with mapping test), datasets, widgets, dashboards,
   variables. — US1, US3
6. User panel & auth. — US4
7. Reprogram RPi devices to the new address + credential; Redis data
   volume left untouched as archive (no migration, FR-023).
8. Pull HTTP → Kafka → gRPC, each only when a real producer exists; collector
   apps (e.g. Home Assistant) use whichever channel exists. — US5, US6

## 5. Phase 0 decisions (`/speckit-plan`, 2026-10-27)

No NEEDS CLARIFICATION left in Technical Context; each choice below in
Decision / Rationale / Alternatives form.

**D1 — Storage: PostgreSQL 16, plain JDBC + HikariCP.**
Rationale: dynamic aggregate SQL (bucket, group by arbitrary JSONB keys) is
easiest as hand-built parameterized SQL; definitions are JSONB documents, so
no ORM mapping is needed. `date_bin()` needs PG ≥ 14.
Alternatives: Exposed / jOOQ (DSL adds little for dynamic JSONB queries),
R2DBC / vertx-pg-client (async gain irrelevant at this scale), TimescaleDB
now (upgrade path, not needed yet), keep Redis (no server-side aggregation).

**D2 — Schema management: one idempotent `schema.sql` run at startup.**
Rationale: v1 schema is ~7 tables, all `CREATE … IF NOT EXISTS`.
`ponytail:` add Flyway at the first `ALTER` after release.
Alternatives: Flyway/Liquibase from day one (machinery before any migration exists).

**D3 — Definitions stored as JSONB documents in one table keyed (org, kind, id).**
Rationale: export/import (FR-017) becomes a dump/upsert of rows; the
`version` field lives in the document; no per-entity tables to evolve.
Users, tokens and source credentials stay relational (secrets never in exports).
Alternatives: normalized tables per entity (many migrations as the format grows).

**D4 — Records: one table `records(org_id, dataset_id, ts, dims jsonb, vals jsonb, ident)`.**
Rationale: dataset-agnostic, matches the universal record; index
`(org_id, dataset_id, ts)`; partial unique `(org_id, dataset_id, ident)` for
dedup via `ON CONFLICT DO NOTHING`.
Ceiling: JSONB extraction cost on multi-million-row ranges may miss SC-004;
upgrade = TimescaleDB hypertable + continuous aggregates, or expression
indexes on hot dims. Measure in quickstart §3 before optimizing.
Measured 2026-10-27 (T077, laptop, Postgres 16 in Docker, 5 M records over
58 days, 10 devices; query = 30-day `auto` → `1h`, `groupBy device`, avg+max):
one query 2.3 s; 8 in parallel p50 9.8 s, p95 12.0 s → **SC-004 missed**
(target < 2 s p95). Ingest (SC-005): 60 s at 48 req/s × 10 native records =
480 records/s, all 200, p95 8 ms → met with batches; one source is capped at
50 requests/s, so the record rate is that times the batch size. SC-004 is
not a release gate; follow-up spec: the upgrade path above (start with a
pre-aggregated hourly table or Timescale continuous aggregates).
Alternatives: table per dataset with typed columns (DDL at runtime), EAV
narrow table (row explosion ×N measures).

**D5 — Mapping language: own minimal path syntax over `JsonElement`.**
Dot paths (`meta.deviceId`), `each` over an object or array with `@key` /
`@.field` inside, casts, constants, constraints (`required`, `min`, `max`,
`notBlank`, `maxAgeHours`). ~100 lines with kotlinx.serialization.
Rationale: covers both device payloads; no new dependency; errors map 1:1
to rejection reasons.
Alternatives: Jayway JsonPath (Jackson/json-smart deps, no expansion
semantics), JSONata (full language, harder to explain in a form editor).

**D6 — Calculated measures: tiny recursive-descent parser → SQL fragment.**
Grammar: numbers, declared measure names, `+ - * /`, parentheses. Measure
names validated against the dataset declaration and inlined as
`(vals->>'name')::float8`; division wrapped in `NULLIF(…,0)`. Never accepts
raw SQL. Alternatives: evaluate in Kotlin after fetch (breaks SQL
aggregation over the derived value), SQL expression column in definition
(injection risk).

**D7 — Query identifiers inlined only after whitelist validation; values always bound.**
Dim/measure names must match the dataset declaration and
`^[A-Za-z_][A-Za-z0-9_]{0,62}$`; filter values, time range and bucket go
as JDBC parameters. `GROUP BY` uses ordinal positions.

**D8 — Auth: platform accounts, PBKDF2-HMAC-SHA256 (JDK, 600k iterations), opaque bearer tokens.**
Token = 32 random bytes, stored as SHA-256 hash with `last_seen`; expires
after 7 days idle (FR-019); sign-out deletes the row. Works the same for
browser (localStorage) and desktop (Preferences); no cookies → no CSRF/CORS
credential handling. First admin bootstrapped from `ADMIN_EMAIL` /
`ADMIN_PASSWORD` env when the users table is empty.
Alternatives: signed session cookies (no server-side revoke; CORS with
credentials for wasm), JWT (revocation needs a table anyway), bcrypt/argon2
libs (new dependency for what the JDK already does).

**D9 — Source credentials: random token per source, hash stored, sent as `Authorization: Bearer`.**
No unauthenticated sources: RPi software gets the header together with the
new address. Rotation = new token, same source id.

**D10 — Source status in memory.**
Per source: minute buckets (60) + hour buckets (24) of accepted/rejected,
`lastRecordAt`, ring buffer of 50 rejections. `ponytail:` resets on restart;
persist to a table if status history across restarts is ever needed.

**D11 — Rate limit: Ktor `RateLimit` plugin keyed by source id.**
Default 50 requests/s per source, 429 + `Retry-After`; configurable per source.

**D12 — Variables resolved in the frontend.**
Dashboard variables (`$device`, `$range`, `$bucket`) are substituted into the
widget query before calling `/api/v2/query`; the backend query stays
stateless and variable-free. Auto bucket = smallest of
`1m,5m,10m,30m,1h,6h,1d` giving ≤ `maxPoints` (default 1000), computed by
the backend when `bucket: "auto"` and echoed in the response.

**D13 — No transition layer: old API deleted first.**
Old API has no real consumers and the device software can change (spec
clarification 2026-10-27), so no aliases, no dual-write, no parity tests.
Alternatives: dual-write / aliases (rejected — code kept alive for nobody).

**D14 — Channels after push: build on demand.**
Pull = coroutine `delay` loop per source + Ktor HttpClient, `identity` for
dedup. Kafka = `kafka-clients`, manual commit after DB commit. gRPC = one
proto `Ingest.Write(stream Payload) returns (stream Ack)` with `grpc-kotlin`,
or OTLP if the first producer already speaks OpenTelemetry. Each lands with
its first real producer (constitution IV).

**D15 — Frontend grid: 12-column flow layout, no drag-and-drop.**
Widgets ordered, each with `w` (1–12) and `h` (rows); rows filled
left-to-right. Charty for line/area/bar; stat, gauge, table are plain Compose.
