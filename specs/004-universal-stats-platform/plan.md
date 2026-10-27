# Implementation Plan: Universal, Configuration-Driven Statistics Platform

**Branch**: `004-universal-stats-platform` (work currently on `feature-2/update-ktor-plugin`) | **Date**: 2026-10-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-universal-stats-platform/spec.md`

## Summary

Replace per-domain plugins and screens with one generic pipeline:
**source → mapping → dataset → record store → query → widget → dashboard**.
Backend (this repo, Ktor) gets a PostgreSQL record + definition store, a
declarative JSON mapper, generic ingest/query/definition APIs under
`/api/v2`, and local accounts with roles. The old v1 plugin API, plugin
modules and Redis code are deleted first — no compatibility layer; RPi
devices are reprogrammed to the new address + credential. Frontend
(`Statistics-UI`, KMP Compose) loses all temperature/epidemic code and gains
a generic dashboard viewer, widget renderers, definition editors and a user
panel. Temperature and epidemic come back as two definition files — the
acceptance test of genericity (spec US2, SC-002). Pull/Kafka/gRPC channels
are designed in but built only when a producer exists (constitution IV).

## Technical Context

**Language/Version**: Kotlin 2.3.0 / JVM (backend); Kotlin 2.3.20-Beta2
Multiplatform — wasmJs, js, JVM desktop (frontend)

**Primary Dependencies**: backend — Ktor 3.4.0 (existing: server-core,
netty, di, content-negotiation, status-pages, cors, swagger, call-logging;
NEW: `ktor-server-auth`, `ktor-server-rate-limit`), kotlinx.serialization,
kotlinx-datetime, NEW `org.postgresql:postgresql` (JDBC) + `HikariCP`;
REMOVED in S0: lettuce, ktor-simple-(redis-)cache, jedis,
testcontainers-redis, `:plugin:*`. Frontend — existing Compose MP,
navigation-compose, Ktor client, Koin, charty; no new deps.

**Storage**: PostgreSQL 16 (Docker Compose) — definitions as JSONB
documents, records in one table with JSONB dims/vals, `date_bin()` buckets
([data-model.md](./data-model.md)). Redis stays running only as untouched
archive.

**Testing**: JUnit 5 + Kotest assertions + `ktor-server-test-host` +
Testcontainers PostgreSQL (backend); `kotlin-test` in `commonTest` for pure
frontend logic (variable substitution, layout rows, unit conversion).

**Target Platform**: Linux server (backend, runs directly or in Compose);
browsers (wasm/js) and desktop JVM (frontend)

**Project Type**: web service + separate multiplatform client (two repos)

**Performance Goals**: SC-004 — 8-widget, 30-day dashboard < 2 s p95 at
10 sources / 5 M records; SC-005 — 500 records/s sustained, queryable < 5 s

**Constraints**: FR-005 ack only after durable commit; every source
authenticated (FR-003); no in-process plugins (constitution I); one organization in v1 with
`org_id` on every owned row (FR-020)

**Scale/Scope**: ≤ 10 users, ≤ 50 sources, ≤ 100 dashboards, millions of
records; ~15 new backend files, ~12 new frontend files, both plugin stacks
deleted

## Constitution Check

*GATE: checked against `.specify/memory/constitution.md` v2.0.0 — before
Phase 0 and re-checked after Phase 1 design.*

- **I. Configuration Over Code** — PASS. Temperature/epidemic become
  `definitions/*.json` (data). No ServiceLoader/jar loading; pull/Kafka/gRPC
  are platform channels, collector apps stay out of process. Old plugin
  code deleted in S0, before anything new is built.
- **II. Generic, Versioned Contracts** — PASS. `/api/v2/**` + `version` field
  in definition files ([contracts/](./contracts/)); v1 API removed, not
  emulated (FR-023).
- **III. Test Coverage for Core Flows** — PASS. Tests planned for mapper,
  expression parser, record store queries, ingest (v2 + native format), query
  API, access control matrix; frontend pure-logic tests only.
- **IV. Local-First, Minimal-Ops** — PASS. Compose Postgres; local accounts
  with PBKDF2 (JDK) and opaque bearer tokens; SSO/multi-org/alerting/
  streaming deferred; pull/Kafka/gRPC gated on a real producer.
- **V. Storage & API Modularity** — PASS. Single `RecordStore` class is the
  only code touching `records`; swap = rewrite that one class. Redis code
  deleted, Redis data untouched.

Post-design re-check (after data-model + contracts): PASS, no new
violations.

## Project Structure

### Documentation (this feature)

```text
specs/004-universal-stats-platform/
├── spec.md
├── plan.md
├── research.md            # build-vs-buy + Phase 0 decisions (§5)
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── api-v2.md          # ingest, query, dims, definitions, status, auth, users
│   └── definition-format.md  # source/dataset/dashboard JSON + temperature/epidemic examples
├── checklists/requirements.md
└── tasks.md               # /speckit-tasks
```

### Source Code — backend (this repo)

```text
.devops/postgres-compose/compose.yaml          # NEW postgres:16
definitions/temperature.json                   # NEW example domain as data (dataset+source+dashboard)
definitions/epidemic.json                      # NEW
src/main/resources/schema.sql                  # NEW CREATE TABLE IF NOT EXISTS …
src/main/resources/application.yaml            # MODIFIED db url/user/pass, bootstrap admin, rate limit
src/main/kotlin/com/anjo/statisticservice/
├── Application.kt                             # MODIFIED wire db, auth, routes
├── db/Db.kt                                   # NEW Hikari DataSource, schema init, tx/query helpers (Dispatchers.IO)
├── definitions/Definitions.kt                 # NEW @Serializable Source/Dataset/Dashboard + DefinitionStore (CRUD, import/export)
├── mapping/Mapper.kt                          # NEW payload → records + constraint checks
├── mapping/Expr.kt                            # NEW calculated-measure expression → SQL fragment
├── records/RecordStore.kt                     # NEW insert/dedup, query (buckets, aggs, at_max), dim values, retention purge
├── ingest/IngestRoutes.kt                     # NEW /api/v2/ingest/{sourceId} (mapping or native format)
├── ingest/SourceStatus.kt                     # NEW in-memory counters + last 50 rejections
├── auth/Auth.kt                               # NEW users, PBKDF2, tokens, bearer provider, role guard, bootstrap admin
├── routing/ApiRoutes.kt                       # NEW query, dims, definitions, status, users, me
├── routing/Routing.kt                         # MODIFIED
├── routing/GlobalExceptionHandler.kt          # MODIFIED MappingException/Forbidden → 400/403
└── di/*                                       # MODIFIED drop Redis/plugin wiring (S0)
src/test/kotlin/com/anjo/statisticservice/
├── testutils/PostgresContainerSetup.kt        # NEW (replaces RedisContainerSetup)
├── mapping/MapperTest.kt, ExprTest.kt         # NEW unit
├── records/RecordStoreTest.kt                 # NEW integration (buckets, aggs, dedup, dims)
├── ingest/IngestRoutesTest.kt                 # NEW v2 ingest with RPI_* sample payloads + native format
└── routing/AccessControlTest.kt               # NEW role × endpoint matrix (SC-008)

DELETED in S0: plugin/**, settings.gradle.kts plugin includes,
plugin/PluginRegistry.kt, routing/PluginStatsRouting.kt,
repository/StatsRepositoryRedisImpl.kt, configuration/RedisClientProvider.kt,
model/RedisConfig.kt, their tests, testutils/RedisContainerSetup.kt,
.docs/*_Contract.md (moved to .docs/archive/)
```

### Source Code — frontend (`/home/diether18/IdeaProjects/Statistics-UI`)

```text
composeApp/src/commonMain/kotlin/com/anjo/statisticsui/
├── api/Models.kt                    # NEW generic mirror of definition format + query req/resp
├── api/PlatformApi.kt               # NEW single Ktor client wrapper (bearer token, all /api/v2 calls)
├── auth/Session.kt                  # NEW token holder + expect TokenStore (actual: webMain localStorage, jvmMain Preferences)
├── presentation/login/LoginScreen.kt            # NEW
├── presentation/home/HomeScreen.kt              # MODIFIED tiles = dashboards (favourites first), not hardcoded domains
├── presentation/dashboard/DashboardScreen.kt    # NEW variable bar, grid, auto-refresh
├── presentation/dashboard/Widgets.kt            # NEW line/area/bar (charty), stat, gauge, table, missing-definition state
├── presentation/dashboard/DashboardEditor.kt    # NEW widget/variable/layout forms
├── presentation/admin/SourcesScreen.kt          # NEW list + status + editor + mapping test
├── presentation/admin/DatasetEditor.kt          # NEW
├── presentation/admin/UsersScreen.kt            # NEW invite, role
├── presentation/account/AccountScreen.kt        # MODIFIED prefs (tz, units, theme, home dashboard)
└── navigation/*                                 # MODIFIED routes carry ids

DELETED: model/dto/*, service/rest/{EpidemicClient,TemperatureClient,ApiUrls}.kt,
presentation/configurationchart/**, presentation/details/**, .docs/*_UI_API_Contract.md
```

**Structure Decision**: keep both existing repos and their Gradle layouts;
backend collapses back to a single application module (plugin subprojects
deleted in S0), packages by pipeline stage. Frontend stays one
`composeApp` module.

## Delivery Slices

No compatibility layer, so slices only need to leave each app building and
starting.

| Slice | Stories | Content | Exit check |
|-------|---------|---------|------------|
| S0 | — | Delete v1 API, `plugin/**`, Redis code + deps, old tests (backend); domain DTOs, clients, `ApiUrls`, screens (frontend). Both apps build and start empty. | `./gradlew build` green in both repos |
| S1 | US1 (backend) | Postgres compose, schema, Db, DefinitionStore, Mapper, RecordStore, source credentials, `/api/v2` ingest/query/dims/definitions/status/mapping-test, native record format. | quickstart §1–2 |
| S2 | US2 (backend) | `definitions/temperature.json`, `epidemic.json`; `each` expansion, `at_max`, calculated measures. | quickstart §3 |
| S3 | US2, US3 (frontend view) | `PlatformApi`, `DashboardScreen`, widgets, variables, auto-refresh. | quickstart §4 |
| S4 | US1, US3 (editors) | Source/dataset/dashboard editors, mapping test UI, export/import. | quickstart §5 |
| S5 | US4 | Auth backend (users, tokens, roles, visibility), login/account/users UI. Until S5 the admin API is open — LAN only. | quickstart §6 |
| S6 | US2 | Reprogram RPi devices: new address + source token header (outside these repos). | quickstart §7 |
| S7+ | US5, US6 | Pull HTTP, Kafka, gRPC — each its own slice when a producer exists; collector apps need nothing new. | quickstart §8 |

## Complexity Tracking

No constitution violations — nothing to justify.
