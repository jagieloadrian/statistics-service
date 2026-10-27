# Tasks: Universal, Configuration-Driven Statistics Platform

**Input**: Design documents from `/specs/004-universal-stats-platform/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api-v2.md, contracts/definition-format.md, quickstart.md

**Tests**: included — constitution III requires tests for ingestion, mapping,
record store, query and access control. Frontend: pure-logic `commonTest` only.

**Organization**: grouped by user story; each story phase ends at a checkpoint
that can be demoed on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1…US6 from spec.md

## Path Conventions

- Backend paths are relative to this repo root
  (`/home/diether18/IdeaProjects/StatisticsService`); Kotlin sources live in
  `src/main/kotlin/com/anjo/statisticservice/`, tests in
  `src/test/kotlin/com/anjo/statisticservice/`.
- Frontend paths start with `Statistics-UI/` =
  `/home/diether18/IdeaProjects/Statistics-UI/`; Kotlin sources live in
  `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/`.
- API shapes: `contracts/api-v2.md`; definition JSON: `contracts/definition-format.md`;
  tables: `data-model.md`; decisions D1–D15: `research.md` §5.

---

## Phase 1: Setup — remove old stack, add new infrastructure (slice S0)

**Purpose**: both apps build and start with zero domain code; Postgres available.

- [X] T001 Delete plugin modules: remove directory `plugin/`, the `include(":plugin:…")` block in `settings.gradle.kts`, and the four `implementation(project(":plugin:…"))` lines in `build.gradle.kts`
- [X] T002 Delete legacy backend code: `src/main/kotlin/com/anjo/statisticservice/plugin/PluginRegistry.kt`, `routing/PluginStatsRouting.kt`, `repository/StatsRepositoryRedisImpl.kt`, `configuration/RedisClientProvider.kt`, `model/RedisConfig.kt`, `di/ShutdownHooks.kt` (and its `registerShutdownHooks()` call in `Application.kt`); empty the `dependencies {}` body in `di/DependencyInjection.kt`; remove `pluginCollectRoute`/`pluginExposeRoutes` calls from `routing/Routing.kt`; remove the `install(SimpleCache)` block from `di/Frameworks.kt`; remove the `ktor.redis` block from `src/main/resources/application.yaml` and `src/test/resources/application-test.yaml`
- [X] T003 Delete legacy tests: `src/test/kotlin/com/anjo/statisticservice/plugin/PluginRegistryTest.kt`, `routing/CollectStatisticRoutesTest.kt`, `routing/StatsEpidemicExposerRoutingTest.kt`, `testutils/RedisContainerSetup.kt`, directory `src/test/resources/redis/`; strip the Redis container from `ApplicationTest.kt` (keep the health/ready tests, they get Postgres in T017)
- [X] T004 Remove Redis/cache deps from `build.gradle.kts`: `lettuce-core`, `ktor-simple-cache`, `ktor-simple-redis-cache`, `kotlinx-coroutines-reactive`, `jedis`, `testcontainers-redis`
- [X] T005 Add `const val API_V2 = "/api/v2"` in new `src/main/kotlin/com/anjo/statisticservice/routing/Api.kt` and point `routing/SwaggerRouting.kt` at `"$API_V2/swagger"` (replaces deleted `ApplicationConstants`)
- [X] T006 [P] Move `.docs/Epidemic_UI_API_Contract.md`, `Temperature_UI_API_Contract.md`, `RPI_Epidemic_Contract.md`, `RPI_Temperature_Contract.md` to `.docs/archive/` (git mv)
- [X] T007 [P] Frontend cleanup: delete `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/model/dto/` (all), `service/rest/EpidemicClient.kt`, `service/rest/TemperatureClient.kt`, `service/rest/ApiUrls.kt`, `presentation/configurationchart/` (all), `presentation/details/` (all), `Statistics-UI/.docs/*_UI_API_Contract.md`; remove `TEMP_DETAILS`/`EPIDEMIC_DETAILS` from `navigation/Screen.kt` and their `composable` entries from `navigation/NavGraph.kt`; make `DashboardContent` in `presentation/home/HomeScreen.kt` render an empty tile list
- [X] T008 [P] Create `.devops/postgres-compose/compose.yaml`: service `postgres` image `postgres:16`, env `POSTGRES_DB=stats`, `POSTGRES_USER=stats`, `POSTGRES_PASSWORD=stats`, port `5432:5432`, volume `./pg-data:/var/lib/postgresql/data`; add `.devops/postgres-compose/pg-data/` to `.gitignore`
- [X] T009 Add deps to `build.gradle.kts`: `org.postgresql:postgresql:42.7.4`, `com.zaxxer:HikariCP:6.2.1`, `io.ktor:ktor-server-auth`, `io.ktor:ktor-server-rate-limit`; test: `org.testcontainers:postgresql:1.20.3`
- [X] T010 Add config to `src/main/resources/application.yaml`: `ktor.db.url: ${DB_URL:jdbc:postgresql://localhost:5432/stats}`, `ktor.db.user: ${DB_USER:stats}`, `ktor.db.password: ${DB_PASSWORD:stats}`, `ktor.auth.adminEmail: ${ADMIN_EMAIL:}`, `ktor.auth.adminPassword: ${ADMIN_PASSWORD:}`, `ktor.cors.hosts: ${CORS_HOSTS:localhost:8081}` (comma-separated UI origins); mirror keys in `src/test/resources/application-test.yaml`
- [X] T011 Verify S0: `./gradlew build` green in this repo; `./gradlew :composeApp:compileKotlinJvm :composeApp:compileKotlinWasmJs` green in `Statistics-UI/`; `grep -rniE 'epidemic|temperature' src ../Statistics-UI/composeApp/src` returns nothing

**Checkpoint**: old stack gone, both repos build (quickstart §0).

---

## Phase 2: Foundational (blocking for all stories)

- [X] T012 Create `src/main/resources/schema.sql` with every table and index from data-model.md (`organizations`, `users`, `auth_tokens`, `definitions`, `dashboard_shares`, `source_credentials`, `records` + `records_range`, `records_ident`), all `IF NOT EXISTS`, ending with `INSERT INTO organizations VALUES (1, 'default') ON CONFLICT DO NOTHING`
- [X] T013 Create `src/main/kotlin/com/anjo/statisticservice/db/Db.kt`: class `Db(url, user, password)` wrapping a HikariDataSource (max pool 10); `init()` runs `schema.sql` from classpath; `suspend fun <T> tx(block: (Connection) -> T): T` on `Dispatchers.IO` with commit/rollback; small `query`/`update` helpers binding params by position; `close()`; constant `ORG = 1` (research D2, FR-020)
- [X] T014 Create `src/main/kotlin/com/anjo/statisticservice/definitions/Definitions.kt` part 1: `@Serializable` models exactly per contracts/definition-format.md — `Dataset`, `Measure(name, type: number|boolean|text, unit?, label?)`, `Dimension`, `Source`, `Channel(type, settings…)`, `Rule(each?, timestamp?, identity, dims, vals, const)`, `TimestampSpec`, `FieldSpec(path?, const?, required, notBlank, min, max, scale)`, `Dashboard`, `Widget`, `Variable`, `Query`, `QueryMeasure(field, agg?, by?, as?)`, `Bundle`; name regex check `^[A-Za-z_][A-Za-z0-9_]{0,62}$` (ids also allow `-`)
- [X] T015 `Definitions.kt` part 2: class `DefinitionStore(db)` — `list(kind)`, `get(kind,id)`, `put(kind,id,doc)` (validate names, `version == 1`, source.dataset exists, mapping outputs only declared names, widget query datasets exist, dashboard `refreshSeconds` null or ≥ 5), `delete(kind,id)` (deleting a dataset also deletes its `records` rows in the same transaction); source credentials: `rotateCredential(sourceId): String` (32 random bytes base64url, store SHA-256 in `source_credentials`), `verifyCredential(sourceId, token)` via `MessageDigest.isEqual` (research D9)
- [X] T016 Rewrite `src/main/kotlin/com/anjo/statisticservice/routing/GlobalExceptionHandler.kt`: `class ApiException(status, code, message, details = emptyList())` responded as `{"error","message","details"}`; `SerializationException`/`IllegalArgumentException` → 400; anything else → 500 logged; delete `exception/Exceptions.kt` if unused
- [X] T017 [P] Create `src/test/kotlin/com/anjo/statisticservice/testutils/PostgresContainerSetup.kt`: one shared `PostgreSQLContainer("postgres:16")`, `testApp { }` helper that runs `testApplication` with db config pointing at the container, and `truncateAll()` for per-test isolation; update `ApplicationTest.kt` to use it
- [X] T018 Update `src/main/kotlin/com/anjo/statisticservice/di/Frameworks.kt`: CORS replace `anyHost()` with `allowHost(…)` for each entry of `ktor.cors.hosts` (admin API is unauthenticated until US4 — any page in a LAN browser could otherwise change definitions), `allowHeader(HttpHeaders.Authorization)`, `allowMethod(Put)`, `allowMethod(Delete)`; ContentNegotiation `Json { encodeDefaults = true; explicitNulls = false }`
- [X] T019 Wire `src/main/kotlin/com/anjo/statisticservice/di/DependencyInjection.kt`: provide `Db` (from `ktor.db.*`, `init()` on start, `close()` on `ApplicationStopped`) and `DefinitionStore`

**Checkpoint**: app starts against Postgres, schema created, `ApplicationTest` green.

---

## Phase 3: User Story 1 — register a source, see data on a chart (P1) 🎯 MVP

**Goal**: admin defines dataset + push source + mapping, data flows in, a widget shows it — no code.

**Independent Test**: quickstart §1–§2 (100 payloads → 100 points on a line widget; native format accepted).

### Tests for US1

- [X] T020 [P] [US1] `src/test/kotlin/com/anjo/statisticservice/mapping/MapperTest.kt`: flat payload → 1 record; casts per measure type; `required`/`min`/`max`/`notBlank`/`maxAgeHours` failures give one reason each in format `vals.<name>: …`; missing timestamp → arrival time + `tsEstimated`; `identity` joined with `|`; extra fields ignored; native format (empty mapping) accepted, undeclared key rejected
- [X] T021 [P] [US1] `src/test/kotlin/com/anjo/statisticservice/records/RecordStoreTest.kt`: insert + dedup (`duplicates` count); raw query ordered by ts; buckets `1m`/`1h` with count/sum/avg/min/max/first/last; `groupBy` + `filter`; `bucket: "none"`; `auto` picks smallest of `1m,5m,10m,30m,1h,6h,1d` giving ≤ maxPoints and echoes it; raw > 10 000 rows → `truncated`; dimension values with first/last/count + search + paging; retention purge
- [X] T022 [P] [US1] `src/test/kotlin/com/anjo/statisticservice/ingest/IngestRoutesTest.kt`: 200 `{"accepted","duplicates"}`; 401 missing/rotated token; 404 unknown source; 400 with reasons and the rejection visible in `/sources/{id}/status`; 503 paused; 429 over rate limit
- [X] T023 [P] [US1] `src/test/kotlin/com/anjo/statisticservice/routing/DefinitionRoutesTest.kt`: PUT/GET/DELETE dataset and source; 400 for unknown dataset reference or undeclared mapping name; `mapping-test` returns records/errors and stores nothing; credential rotation returns token once

### Implementation for US1 — backend

- [X] T024 [US1] `src/main/kotlin/com/anjo/statisticservice/mapping/Mapper.kt`: `object Mapper { fun map(source: Source, dataset: Dataset, payload: JsonElement, now: Instant): List<Record> }` — dot-path lookup on `JsonElement`, timestamp formats `iso` (instant, or local date-time read as UTC) / `epoch_ms` / `epoch_s`, casts, `scale`, constraints, identity; empty mapping = native record format (one object or array, FR-025); collects all reasons and throws `MappingException(reasons)`; `data class Record(datasetId, ts, tsEstimated, dims: Map<String,String>, vals: Map<String, JsonPrimitive>, ident: String?)` (research D5)
- [X] T025 [US1] `src/main/kotlin/com/anjo/statisticservice/records/RecordStore.kt` write side: `insert(records): Pair<accepted, duplicates>` in one transaction with `ON CONFLICT DO NOTHING`; `purgeRetention(datasets)`
- [X] T026 [US1] `RecordStore.kt` read side: `query(q: Query, dataset: Dataset): QueryResult` building SQL per research D4/D7/D12 — identifiers validated against the dataset and inlined, values bound, `date_bin` buckets aligned to UTC epoch, `GROUP BY` ordinals, aggs count/sum/avg/min/max/first/last (`(array_agg(x ORDER BY ts))[1]` style), default aggs per contracts/api-v2.md, raw cap 10 000; result shape `{bucket, series:[{dims, columns, rows}], truncated}`; `dimensionValues(dataset, dim, search, limit, offset)`
- [X] T027 [P] [US1] `src/main/kotlin/com/anjo/statisticservice/ingest/SourceStatus.kt`: in-memory per source — 60 minute buckets + 24 hour buckets of accepted/rejected, `lastRecordAt`, ring buffer of 50 `{at, reason, excerpt ≤ 500 chars}`; `state(source)` derives waiting-for-data / receiving / silent (`silentAfterMinutes`) / erroring (last 5 attempts rejected) / paused (research D10, data-model state diagram)
- [X] T028 [US1] `src/main/kotlin/com/anjo/statisticservice/ingest/IngestRoutes.kt`: `POST $API_V2/ingest/{sourceId}` — 404 unknown, 503 + `Retry-After` paused, 401 bad bearer, rate limit via Ktor `RateLimit` keyed by source id using `rateLimitPerSecond` (research D11; if per-key limits are not supported by the plugin API, use a fixed 50/s and leave a `ponytail:` note), Mapper → RecordStore.insert → SourceStatus; MappingException → 400 + status rejection; `describe {}` OpenAPI summary on the route (constitution workflow: contract docs in the same change)
- [X] T029 [US1] `src/main/kotlin/com/anjo/statisticservice/routing/ApiRoutes.kt`: `GET/PUT/DELETE $API_V2/{datasets|sources|dashboards}[/{id}]`, `POST /sources/{id}/credential`, `GET /sources/{id}/status`, `POST /sources/{id}/mapping-test`, `POST /query`, `GET /datasets/{id}/dimensions/{dim}/values` per contracts/api-v2.md, each with a `describe {}` OpenAPI summary (no auth yet — LAN only until US4)
- [X] T030 [US1] Register `RecordStore`, `SourceStatus` in `di/DependencyInjection.kt`; call ingest + API routes from `routing/Routing.kt`; launch hourly retention coroutine (`while (isActive) { purgeRetention(); delay(1.hours) }`) in `Application.kt`

### Implementation for US1 — frontend

- [X] T031 [P] [US1] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/api/Models.kt`: `@Serializable` mirror of definition-format (Dataset, Source, Rule, FieldSpec, Dashboard, Widget, Variable, Query, QueryMeasure, Bundle) + `QueryResult`, `SourceStatus`, `DimensionValue`, `ApiError`; JSON with `ignoreUnknownKeys = true`
- [X] T032 [US1] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/api/PlatformApi.kt`: Koin `@Singleton` wrapping the existing `HttpClient` + `AppConfig.backendUrl`; one suspend function per endpoint used by the UI (definitions CRUD, credential, status, mapping-test, query, dimension values); non-2xx → `ApiException(message from ApiError)`
- [X] T033 [US1] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/admin/DatasetEditor.kt`: list + form for id/label, measures (name, type, unit, label), dimensions, calculated measures (name + expression; the server validates, show its error inline), retention days; save via PUT, show API errors inline
- [X] T034 [US1] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/admin/SourcesScreen.kt`: source list with state chip from status; editor with id/label/dataset/rate limit, mapping rows (target name → path, required, min, max) plus an "advanced JSON" text area for the full mapping; "Test mapping" (paste sample payload → records/errors); "Rotate credential" shows token once; pause/resume toggle (PUT with `paused`); status panel with counters and last rejections
- [X] T035 [US1] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/dashboard/Widgets.kt`: `@Composable fun Widget(widget, result)` dispatching on `type`; implement `line` (charty, one line per series × measure, x = `ts`) and `table`; loading and error states
- [X] T036 [US1] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/dashboard/DashboardScreen.kt`: load dashboard by id, run each widget query in parallel, render widgets in a column; dashboard-level time range presets (last 1h/24h/7d/30d, default from `defaultRange`) setting `from`/`to` on every widget query; "Add widget" dialog (dataset, measures + agg, groupBy, bucket, type line/table — no per-widget range) saving the dashboard via PUT; "New dashboard" creates an empty one
- [X] T037 [US1] Navigation: in `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/navigation/Screen.kt` and `NavGraph.kt` add routes `dashboard/{id}`, `sources`, `datasets`; `presentation/home/HomeScreen.kt` tiles = `GET /dashboards` (title, tap → dashboard) plus entries for Sources and Datasets

**Checkpoint**: quickstart §1–§2 pass; MVP demoable.

---

## Phase 4: User Story 2 — temperature and epidemic as pure configuration (P1)

**Goal**: both domains run from definition files; nested `byType`, run summaries and variables work.

**Independent Test**: quickstart §3 + dashboards render in the UI (§4 first half).

### Tests for US2

- [X] T038 [P] [US2] Extend `MapperTest.kt`: epidemic sample payload (copy from `.docs/archive/RPI_Epidemic_Contract.md` into `src/test/resources/payloads/epidemic.json`) → 4 records (`type` = `total`, `0`, `1`, `2`), `@key`/`@.x` resolution, `const`, identity includes the key
- [X] T039 [P] [US2] `src/test/kotlin/com/anjo/statisticservice/mapping/ExprTest.kt`: precedence, parentheses, numbers, unknown name rejected, non-numeric measure rejected, any SQL-looking input (`;`, quotes, `--`) rejected, division emits `NULLIF(…,0)`
- [X] T040 [P] [US2] Extend `RecordStoreTest.kt`: `at_max`/`at_min` with `by`, `ts` with min/max, calculated measure in raw and bucketed queries, division by zero → null
- [X] T041 [P] [US2] `src/test/kotlin/com/anjo/statisticservice/ExamplesTest.kt`: import `definitions/temperature.json` and `definitions/epidemic.json`, ingest `src/test/resources/payloads/temperature.json` / `epidemic.json`, assert every widget query of both dashboards returns the expected values (min/avg/max; peakInfected, timeToPeak, finalRecovered, finalDead, attackRate)

### Implementation for US2

- [X] T042 [US2] `mapping/Mapper.kt`: `each` over object entries / array items with `@key` and `@.x`, `const` dimensions; all records of one payload inserted atomically (already one transaction in T025)
- [X] T043 [P] [US2] `src/main/kotlin/com/anjo/statisticservice/mapping/Expr.kt`: recursive-descent parser for `+ - * / ( )`, numbers, measure names → SQL fragment `(vals->>'name')::float8`, `NULLIF` on divisors (research D6); `DefinitionStore.put` validates every `calculated` expression
- [X] T044 [US2] `records/RecordStore.kt`: `at_max`/`at_min` (`(array_agg(field ORDER BY by DESC NULLS LAST))[1]`), `ts` as aggregatable field, calculated measures resolved through `Expr`
- [X] T045 [P] [US2] `definitions/temperature.json`: bundle with dataset `temperature` (temperature °C, humidity %, dim `device`), push source `temperature` (mapping per definition-format example, payload shape from `.docs/archive/RPI_Temperature_Contract.md`), dashboard `temperature` (variables `device`, `range`, `bucket` options `raw,1m,1h,1d`; widgets: devices table with first/last seen, line temperature+humidity, stat min/avg/max)
- [X] T046 [P] [US2] `definitions/epidemic.json`: bundle with dataset, source and dashboard `epidemic-runs` exactly as the epidemic example in contracts/definition-format.md
- [X] T047 [P] [US2] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/dashboard/Variables.kt`: pure `fun resolve(query: Query, values: Map<String, VariableValue>, range: TimeRange): Query` replacing `$name` in `filter` values and `bucket`, and setting `from`/`to` from the dashboard time range (`timeRange` variable, else `defaultRange`) + `VariableBar` composable (dimension picker from dimension values with search, bucket selector; a `timeRange` variable binds the dashboard time range selector from T036 instead of adding a second one)
- [X] T048 [P] [US2] `Statistics-UI/composeApp/src/commonTest/kotlin/com/anjo/statisticsui/VariablesTest.kt`: substitution in filter lists and bucket, `from`/`to` from `timeRange` variable and from `defaultRange`; unknown variable left as error
- [X] T049 [US2] `presentation/dashboard/Widgets.kt`: add `stat` (one value per column, label from `as`) and `bar`; `line` honours `options.x` (e.g. `generation`); `DashboardScreen.kt` shows `VariableBar` and re-queries all widgets on change without reload; show the applied bucket from the response
- [X] T050 [P] [US2] `.docs/Device_Ingest.md`: how a device sends to the platform — URL `/api/v2/ingest/{sourceId}`, header `Authorization: Bearer <source token>`, payload unchanged or native format, response codes (replaces the archived RPI contracts, constitution workflow)

**Checkpoint**: temperature + epidemic dashboards work from definitions alone (SC-002).

---

## Phase 5: User Story 3 — build dashboards with variables (P2)

**Goal**: full dashboard builder: grid, all widget types, variables editor, refresh, export/import.

**Independent Test**: spec US3 independent test; quickstart §4 second half + §5.

### Tests for US3

- [X] T051 [P] [US3] `src/test/kotlin/com/anjo/statisticservice/routing/ImportExportTest.kt`: export → import into a truncated DB reproduces identical documents; credentials never exported; invalid bundle → nothing imported; dataset change removing a measure referenced by a widget → 409 listing widget refs, `?force=true` succeeds; deleting a dataset removes its records
- [X] T052 [P] [US3] `Statistics-UI/composeApp/src/commonTest/kotlin/com/anjo/statisticsui/LayoutTest.kt`: 12-column flow layout groups widgets into rows by `w`

### Implementation for US3

- [X] T053 [US3] `routing/ApiRoutes.kt` + `definitions/Definitions.kt`: `GET $API_V2/export`, `POST /import` (one transaction, all-or-nothing, `{created, updated, errors}`); reference guard on dataset/source PUT/DELETE → 409 with referencing widget ids unless `?force=true`; `describe {}` OpenAPI summaries on the new routes
- [X] T054 [P] [US3] `presentation/dashboard/Widgets.kt`: add `area` and `gauge` (Canvas arc with `min`/`max`/`thresholds`); apply `options.unit`, `colors`, `decimals`, `thresholds`; "missing data definition" state when the query returns 400 for an unknown dataset/field; "no data in range" state
- [X] T055 [US3] `presentation/dashboard/DashboardScreen.kt`: 12-column grid (`w` 1–12, `h` rows, rows filled left to right, function shared with LayoutTest); auto-refresh loop every `refreshSeconds` while the screen is visible
- [X] T056 [US3] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/dashboard/DashboardEditor.kt`: edit title, default range, refresh; add/edit/delete/reorder widgets with all fields from the Widget table in definition-format; variable editor (dimension, timeRange, bucket); save via PUT
- [X] T057 [US3] Export/import UI in `presentation/admin/SourcesScreen.kt` (or a small section on the home screen): export shows the bundle JSON in a copyable text area; import pastes JSON and shows `{created, updated, errors}`

**Checkpoint**: dashboards fully built in the UI; export → import round-trips (SC-007).

---

## Phase 6: User Story 4 — user panel: accounts, roles, sharing (P2)

**Goal**: sign-in, roles admin/editor/viewer, visibility + shares, preferences.

**Independent Test**: spec US4 independent test; quickstart §6.

### Tests for US4

- [X] T058 [P] [US4] `src/test/kotlin/com/anjo/statisticservice/auth/AuthTest.kt`: PBKDF2 hash/verify; login ok / wrong password (generic 401) / 6th attempt in a minute → 429; logout revokes token; token idle > 7 days rejected; password change revokes other tokens; password < 10 chars → 400 on user create and password change; bootstrap admin created only when `users` is empty; last admin cannot be demoted/deleted (409)
- [X] T059 [P] [US4] `src/test/kotlin/com/anjo/statisticservice/routing/AccessControlTest.kt`: role × endpoint matrix from data-model.md (admin/editor/viewer/anonymous × definitions, status, credential, import/export, users, query, dashboards private/users/all); viewer query on a dataset not used by a visible dashboard → 403 (SC-008)

### Implementation for US4 — backend

- [X] T060 [US4] `src/main/kotlin/com/anjo/statisticservice/auth/Auth.kt`: PBKDF2WithHmacSHA256 600 000 iterations, 16-byte salt, stored `pbkdf2$600000$<salt b64>$<hash b64>`; token issue (32 random bytes base64url, SHA-256 in `auth_tokens`), validate + idle expiry 7 days + `last_seen` update at most once per minute; Ktor `bearer("user")` provider putting `UserPrincipal(id, role)`; `requireRole(vararg roles)` helper throwing `ApiException(403)`; bootstrap admin from `ktor.auth.adminEmail/adminPassword` when `users` is empty (research D8)
- [X] T061 [US4] `routing/ApiRoutes.kt`: `/auth/login` (RateLimit provider `login`, 5/min per remote host), `/auth/logout`, `GET /me`, `PUT /me/prefs`, `PUT /me/password`, `GET/POST/PUT/DELETE /users` per contracts/api-v2.md; passwords < 10 chars rejected with 400; `describe {}` OpenAPI summaries on every route
- [X] T062 [US4] `routing/ApiRoutes.kt` + `definitions/Definitions.kt`: wrap all non-ingest `/api/v2` routes in `authenticate("user")` with role checks per the data-model matrix; dashboards get `owner_id` on create, list/get filtered by visibility (`private` owner only, `users` via `dashboard_shares`, `all`); `PUT /dashboards/{id}/shares` (with `describe {}` OpenAPI summary); viewer queries limited to datasets used by dashboards visible to them; export for editors limited to own + shared dashboards; extend `testutils/PostgresContainerSetup.kt` so `testApp` creates an admin and attaches its bearer token by default, and update the earlier route tests (T022, T023, T041, T051) to pass with auth on

### Implementation for US4 — frontend

- [ ] T063 [P] [US4] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/auth/Session.kt`: `expect class TokenStore { fun load(): String?; fun save(token: String?) }` + `Session` (current token, user, `signOut()`); actuals in `Statistics-UI/composeApp/src/webMain/kotlin/com/anjo/statisticsui/auth/TokenStore.web.kt` (`localStorage`; add `org.jetbrains.kotlinx:kotlinx-browser` only if `kotlinx.browser` is missing for wasm) and `Statistics-UI/composeApp/src/jvmMain/kotlin/com/anjo/statisticsui/auth/TokenStore.jvm.kt` (`java.util.prefs.Preferences`)
- [ ] T064 [US4] `api/PlatformApi.kt`: send `Authorization: Bearer` from `Session`; 401 → `Session.signOut()` and navigate to login; add auth/me/users/shares calls
- [ ] T065 [US4] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/login/LoginScreen.kt` + `navigation/NavGraph.kt`: Splash → login when no valid token, else home dashboard from prefs or home screen; sign-out entry in the drawer
- [ ] T066 [US4] `presentation/account/AccountScreen.kt` (replace the Account placeholder): time zone, units (metric/imperial), theme, home dashboard, change password; timestamps in widgets rendered in the chosen zone (`kotlinx-datetime`); unit display conversion in new `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/dashboard/Units.kt` — one table of `(from, to, factor, offset)` entries per the unit table in contracts/definition-format.md, used by every widget renderer for values and axis labels, unknown units shown as declared; theme applied in `App.kt`
- [ ] T067 [P] [US4] `Statistics-UI/composeApp/src/commonTest/kotlin/com/anjo/statisticsui/UnitsTest.kt`: one metric→imperial and one imperial→metric case per family of the unit table (°C↔°F with offset, km↔mi, kg↔lb, km/h↔mph, l↔gal, hPa↔inHg), round-trip within 1e-9, pass-through for unknown units
- [ ] T068 [US4] `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/admin/UsersScreen.kt` (admin: list, create with initial password, change role, delete); hide Sources/Datasets/Users entries for non-admins; visibility + share picker in `DashboardEditor.kt`; favourite star on home tiles (favourites first)

**Checkpoint**: quickstart §6 passes; platform safe to expose beyond the LAN.

---

## Phase 7: User Story 5 — pull, Kafka, gRPC channels (P3)

**Gate**: build each channel only when a real producer for it exists (constitution IV, research D14). Each task pair is independent.

- [ ] T069 (gated: no pull producer yet) [P] [US5] Pull channel: `src/main/kotlin/com/anjo/statisticservice/ingest/PullRunner.kt` — one coroutine per active `pull` source (`delay(intervalSeconds)` loop, Ktor HttpClient with configured headers, optional `itemsPath` → one payload per item, back-off on failure, error into `SourceStatus`, `identity` dedup), started/stopped on source PUT/DELETE/pause; test `src/test/kotlin/com/anjo/statisticservice/ingest/PullRunnerTest.kt` with a stub server (1 000 records, restart → no duplicates, unreachable → status error)
- [ ] T070 (gated: no Kafka producer yet) [P] [US5] Kafka channel: dep `org.apache.kafka:kafka-clients`; `src/main/kotlin/com/anjo/statisticservice/ingest/KafkaRunner.kt` — consumer per `kafka` source, manual offset commit only after `RecordStore.insert` commit; test with Testcontainers Kafka in `src/test/kotlin/com/anjo/statisticservice/ingest/KafkaRunnerTest.kt` (restart mid-stream → no loss/duplicates)
- [ ] T071 (gated: no gRPC producer yet) [P] [US5] gRPC channel: `src/main/proto/ingest.proto` (`Ingest.Write(stream Payload) returns (stream Ack)`, payload = source id + token + JSON bytes) with `grpc-kotlin`, or OTLP if the first producer already speaks OpenTelemetry; per-record accepted/rejected ack; test in `src/test/kotlin/com/anjo/statisticservice/ingest/GrpcIngestTest.kt`
- [ ] T072 [US5] Channel settings fields for `pull`/`kafka`/`grpc` in `Statistics-UI/composeApp/src/commonMain/kotlin/com/anjo/statisticsui/presentation/admin/SourcesScreen.kt` (only for channels built above)

**Checkpoint**: each built channel passes quickstart §8.

---

## Phase 8: User Story 6 — custom logic via collector apps (P3)

- [X] T073 [P] [US6] `examples/collector.py`: stdlib-only (`urllib`, `json`, `time`) script that polls a URL every N seconds and pushes native records to `/api/v2/ingest/{sourceId}` with a source token; env vars for URL, source id, token
- [X] T074 [US6] `.docs/Collector_Apps.md`: when to write a collector instead of a mapping, auth, native format, retry on 429/503; link from `.docs/Device_Ingest.md`

**Checkpoint**: quickstart §8 collector check passes (killing it affects nothing else).

---

## Phase 9: Polish & Cross-Cutting

- [X] T075 [P] OpenAPI review: open `$API_V2/swagger`, confirm every `/api/v2` route from contracts/api-v2.md is listed with its summary (added per story in T028, T029, T053, T061); fill any gap in `routing/ApiRoutes.kt` / `ingest/IngestRoutes.kt`
- [ ] T076 [P] `README.md` (backend): run Postgres compose, env vars (`DB_*`, `ADMIN_*`), import example definitions; `Statistics-UI/README.md`: backend URL, login
- [X] T077 Performance check per quickstart §3 (5 M seeded records, 8 parallel 30-day `auto` queries < 2 s p95, SC-004) and 500 records/s ingest for 1 min (SC-005); record numbers under research.md D4; not a release gate (spec SC-004/005 note) — if missed, open a follow-up spec for the D4 upgrade path, the feature still closes
- [ ] T078 Run quickstart §0–§7 end to end; fix gaps — 2026-10-27: backend §0–§3, §5 export, §6, §7 and the §8 collector check pass against a real server; §4/§5 UI flows still need a manual run in the desktop app
- [ ] T079 Update `PROJECT-STATUS.md` (completed work, decisions, next steps)

---

## Dependencies & Execution Order

- **Setup (T001–T011)** → **Foundational (T012–T019)** → stories.
- **US1** (T020–T037) depends on Foundational only. MVP.
- **US2** depends on US1 (Mapper, RecordStore, Widgets, DashboardScreen).
- **US3** depends on US1; uses US2's `Variables.kt` (T047) for the variable editor.
- **US4** depends on US1 backend routes (wraps them); frontend parts touch US1/US3 screens — do after US3 to avoid rework.
- **US5** depends on US1 ingest pipeline; each channel independent; gated on a producer.
- **US6** depends on US1 (push + native format) only.
- **Polish** after the stories you ship.

Story order: US1 → US2 → US3 → US4 → (US6) → US5 when needed.

## Parallel Opportunities

- Setup: T006, T007, T008 alongside T001–T005 (different repos/files).
- Foundational: T017 alongside T012–T016.
- US1: tests T020–T023 together; T027 and T031 alongside backend work; frontend T031–T037 can start once contracts are stable (they target contracts/api-v2.md, not the code).
- US2: T038–T041 together; T043, T045, T046, T047, T048, T050 together.
- US3: T051, T052, T054 together.
- US4: T058, T059, T063, T067 together.
- US5: T069, T070, T071 fully independent.

### Example: US1 kick-off

```text
Parallel: T020 MapperTest | T021 RecordStoreTest | T022 IngestRoutesTest | T023 DefinitionRoutesTest | T031 UI Models
Then:     T024 Mapper → T025/T026 RecordStore → T028 IngestRoutes, T029 ApiRoutes → T030 wiring
UI:       T032 PlatformApi → T033/T034 admin screens, T035 Widgets → T036 DashboardScreen → T037 navigation
```

## Implementation Strategy

1. **MVP** = Setup + Foundational + US1: any JSON source charted without code. Stop, run quickstart §1–§2.
2. **+US2**: temperature + epidemic as definitions; reprogram the RPi devices (quickstart §7) — first real data.
3. **+US3**: full dashboard builder, export/import.
4. **+US4**: accounts and roles — required before exposing outside the LAN.
5. **US6 / US5**: collector doc anytime; channels only when a producer exists.

Commit after each task or logical group (one atomic conventional commit per unit, after its tests pass).
