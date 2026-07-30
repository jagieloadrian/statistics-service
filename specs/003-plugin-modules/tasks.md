# Tasks: Extract Plugins into Separate `/plugin` Packages (Phase 2.5)

**Input**: Design documents from `/specs/003-plugin-modules/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/module-boundaries.md, quickstart.md

**Tests**: existing tests move with their code (no new test-writing framework introduced); a few new/split tests are called out explicitly where the move requires it.

**Organization**: tasks grouped by user story per spec.md priorities (US1 P1, US2 P2, US3 P3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: which user story this task belongs to
- All paths are repo-relative from `/home/diether18/IdeaProjects/StatisticsService`

---

## Phase 1: Setup (Gradle module skeletons)

**Purpose**: wire the 4 new Gradle subprojects into the build before any code moves

- [X] T001 Add `include(":plugin:plugin-api", ":plugin:rpi_epidemic_api", ":plugin:rpi_temperature_api", ":plugin:home_assistant_api")` to `settings.gradle.kts`
- [X] T002 [P] Create `plugin/plugin-api/build.gradle.kts` — `kotlin("jvm")`, `implementation("io.ktor:ktor-server-core")` (for `Route`), `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")` (for `Flow` in `StatsRepository`), no other project dependency
- [X] T003 [P] Create `plugin/rpi_epidemic_api/build.gradle.kts` — `kotlin("jvm")`, `kotlin("plugin.serialization")`, `implementation(project(":plugin:plugin-api"))`, `implementation("io.ktor:ktor-server-core")`, `implementation("io.ktor:ktor-serialization-kotlinx-json")`, `implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")`, `implementation("io.github.oshai:kotlin-logging-jvm:7.0.14")`, `testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")`
- [X] T004 [P] Create `plugin/rpi_temperature_api/build.gradle.kts` — same shape as T003
- [X] T005 [P] Create `plugin/home_assistant_api/build.gradle.kts` — `kotlin("jvm")`, `implementation(project(":plugin:plugin-api"))`, `implementation("io.ktor:ktor-server-core")` only
- [X] T006 Add `implementation(project(":plugin:plugin-api"))`, `implementation(project(":plugin:rpi_epidemic_api"))`, `implementation(project(":plugin:rpi_temperature_api"))`, `implementation(project(":plugin:home_assistant_api"))` to root `build.gradle.kts`

**Checkpoint**: `./gradlew projects` lists all 4 subprojects; root build still compiles with old code untouched (new modules are empty).

---

## Phase 2: Foundational (`plugin-api` — blocks every other module)

**Purpose**: the shared contract every plugin module depends on; nothing in Phase 3+ can compile until this exists

**⚠️ CRITICAL**: complete before any user story work

- [X] T007 [P] Move `src/main/kotlin/com/anjo/statisticservice/plugin/StatPlugin.kt` to `plugin/plugin-api/src/main/kotlin/com/anjo/statisticservice/plugin/StatPlugin.kt` (delete from `src`)
- [X] T008 [P] Move `src/main/kotlin/com/anjo/statisticservice/plugin/PluginRouteSet.kt` to `plugin/plugin-api/src/main/kotlin/com/anjo/statisticservice/plugin/PluginRouteSet.kt` (delete from `src`)
- [X] T009 [P] Move `src/main/kotlin/com/anjo/statisticservice/repository/StatsRepository.kt` (interface only) to `plugin/plugin-api/src/main/kotlin/com/anjo/statisticservice/repository/StatsRepository.kt` (delete from `src`; `StatsRepositoryRedisImpl` stays in `src` and now implements the moved interface via the `plugin-api` dependency)
- [X] T010 Create `plugin/plugin-api/src/main/kotlin/com/anjo/statisticservice/utils/ApplicationConstants.kt` containing only `API_BASE_PATH` and `getKey(pluginId, deviceId, runId?)`; remove `getEpidemicKey`/`getTemperatureKey`/`EPIDEMIC_KEYS`/`TEMPERATURE_KEYS` from `src/main/kotlin/com/anjo/statisticservice/utils/ApplicationConstants.kt` (they relocate in Phase 3/4)
- [X] T011 Create `plugin/plugin-api/src/main/kotlin/com/anjo/statisticservice/utils/DbKeyConstants.kt` containing only the 3 fields marked `//common` today (`DEVICE_ID_KEY`, `RUN_ID_KEY`, `TIMESTAMP_KEY`); delete `src/main/kotlin/com/anjo/statisticservice/utils/DbKeyConstants.kt`'s domain fields (full file deletion happens once Phase 3/4 relocate the rest)
- [X] T012 Update `src/main/kotlin/com/anjo/statisticservice/plugin/PluginRegistry.kt`, `src/main/kotlin/com/anjo/statisticservice/di/ShutdownHooks.kt`, `src/main/kotlin/com/anjo/statisticservice/routing/PluginStatsRouting.kt`, `src/main/kotlin/com/anjo/statisticservice/routing/SwaggerRouting.kt` — no import changes needed since package names are unchanged (`com.anjo.statisticservice.plugin`, `.repository`, `.utils`), just confirm they now resolve against the `plugin-api` module dependency (added in T006)

**Checkpoint**: `./gradlew :plugin:plugin-api:build` succeeds standalone; root build fails to compile (expected — `EpidemicPlugin`/`TemperaturePlugin`/`StatsCollectorService` still reference now-moved/trimmed types) until Phase 3 completes.

---

## Phase 3: User Story 1 - Existing epidemic/temperature clients see no change (Priority: P1) 🎯 MVP

**Goal**: `EpidemicPlugin`/`TemperaturePlugin` and everything they depend on live in their own modules; collect/expose endpoints behave byte-identically to before the move.

**Independent Test**: `./gradlew test` — existing `CollectStatisticRoutesTest` and `StatsEpidemicExposerRoutingTest` pass unchanged.

### Epidemic module (rpi_epidemic_api)

- [X] T013 [P] [US1] Create `plugin/rpi_epidemic_api/src/main/kotlin/com/anjo/statisticservice/utils/EpidemicKeyConstants.kt` with `EPIDEMIC_KEYS` + `getEpidemicKey(deviceId, runId)`, carved from `src/main/kotlin/com/anjo/statisticservice/utils/ApplicationConstants.kt`'s pre-T010 content
- [X] T014 [P] [US1] Create `plugin/rpi_epidemic_api/src/main/kotlin/com/anjo/statisticservice/utils/EpidemicDbKeyConstants.kt` with the epidemic-only fields carved from `src/main/kotlin/com/anjo/statisticservice/utils/DbKeyConstants.kt` (`STARTED_AT_KEY`, `ENDED_AT_KEY`, `POPULATION_KEY`, `GENERATION_KEY`, `INFECTED_KEY`, `SUSCEPTIBLE_KEY`, `RECOVERED_KEY`, `DEAD_KEY`, `EXPOSED_KEY`, `LOCKDOWN_KEY`, `MOBILITY_MULTIPLICATION_KEY`, `BY_TYPE_KEY`)
- [X] T015 [P] [US1] Move `src/main/kotlin/com/anjo/statisticservice/model/dto/EpidemicDto.kt` and `src/main/kotlin/com/anjo/statisticservice/model/responsedto/EpidemicResponseDto.kt` to the equivalent paths under `plugin/rpi_epidemic_api/src/main/kotlin/...`
- [X] T016 [P] [US1] Move `isEpidemicValid` (from `src/main/kotlin/com/anjo/statisticservice/validation/`) to `plugin/rpi_epidemic_api/src/main/kotlin/com/anjo/statisticservice/validation/`
- [X] T017 [US1] Create `plugin/rpi_epidemic_api/src/main/kotlin/com/anjo/statisticservice/service/EpidemicStatsCollectorService.kt` — `saveEpidemicStats(dto)` + `prepareEpidemicBody`/`saveStatistics` helpers, carved verbatim from `src/main/kotlin/com/anjo/statisticservice/service/StatsCollectorService.kt`, using `StatsRepository` (from `plugin-api`) and `EpidemicKeyConstants`/`EpidemicDbKeyConstants` (depends on T013, T014)
- [X] T018 [US1] Move `src/main/kotlin/com/anjo/statisticservice/service/exposer/EpidemicStatsExposerService.kt` to `plugin/rpi_epidemic_api/src/main/kotlin/com/anjo/statisticservice/service/exposer/EpidemicStatsExposerService.kt`, repointing its `ApplicationConstants.getEpidemicKey`/`DbKeyConstants.*` imports to `EpidemicKeyConstants`/`EpidemicDbKeyConstants` (depends on T013, T014)
- [X] T019 [US1] Move `src/main/kotlin/com/anjo/statisticservice/plugin/EpidemicPlugin.kt` to `plugin/rpi_epidemic_api/src/main/kotlin/com/anjo/statisticservice/plugin/EpidemicPlugin.kt`, changing its constructor to take `EpidemicStatsCollectorService` instead of core's `StatsCollectorService` (depends on T017, T018)
- [X] T020 [P] [US1] Move `src/test/kotlin/com/anjo/statisticservice/plugin/EpidemicPluginTest.kt` to `plugin/rpi_epidemic_api/src/test/kotlin/com/anjo/statisticservice/plugin/EpidemicPluginTest.kt`, updating it to construct against `EpidemicStatsCollectorService`
- [X] T021 [P] [US1] Move `src/test/kotlin/com/anjo/statisticservice/service/exposer/EpidemicStatsExposerServiceTest.kt` to `plugin/rpi_epidemic_api/src/test/kotlin/com/anjo/statisticservice/service/exposer/EpidemicStatsExposerServiceTest.kt`
- [X] T022 [P] [US1] Extract the epidemic assertions from `src/test/kotlin/com/anjo/statisticservice/validation/ValidationRequestBodyFuncsTest.kt` into `plugin/rpi_epidemic_api/src/test/kotlin/com/anjo/statisticservice/validation/EpidemicValidationTest.kt`

### Temperature module (rpi_temperature_api)

- [X] T023 [P] [US1] Create `plugin/rpi_temperature_api/src/main/kotlin/com/anjo/statisticservice/utils/TemperatureKeyConstants.kt` with `TEMPERATURE_KEYS` + `getTemperatureKey(deviceId)`
- [X] T024 [P] [US1] Create `plugin/rpi_temperature_api/src/main/kotlin/com/anjo/statisticservice/utils/TemperatureDbKeyConstants.kt` with `STATUS_KEY`, `TEMPERATURE_KEY`, `HUMIDITY_KEY`
- [X] T025 [P] [US1] Move `src/main/kotlin/com/anjo/statisticservice/model/Resolution.kt`, `src/main/kotlin/com/anjo/statisticservice/model/dto/TemperatureDto.kt`, `src/main/kotlin/com/anjo/statisticservice/model/responsedto/TemperatureResponseDto.kt` to the equivalent paths under `plugin/rpi_temperature_api/src/main/kotlin/...`
- [X] T026 [P] [US1] Move `isTemperatureDtoValid` to `plugin/rpi_temperature_api/src/main/kotlin/com/anjo/statisticservice/validation/`
- [X] T027 [US1] Create `plugin/rpi_temperature_api/src/main/kotlin/com/anjo/statisticservice/service/TemperatureStatsCollectorService.kt` — `saveTemperatureStats(dto)` + `prepareTemperatureBody`/`saveStatistics` helpers, carved verbatim from `StatsCollectorService.kt` (depends on T023, T024)
- [X] T028 [US1] Move `src/main/kotlin/com/anjo/statisticservice/service/exposer/TemperatureStatsExposerService.kt` to `plugin/rpi_temperature_api/src/main/kotlin/com/anjo/statisticservice/service/exposer/TemperatureStatsExposerService.kt`, repointing key/field-constant imports (depends on T023, T024)
- [X] T029 [US1] Move `src/main/kotlin/com/anjo/statisticservice/plugin/TemperaturePlugin.kt` to `plugin/rpi_temperature_api/src/main/kotlin/com/anjo/statisticservice/plugin/TemperaturePlugin.kt`, changing its constructor to take `TemperatureStatsCollectorService` (depends on T027, T028)
- [X] T030 [P] [US1] Move `src/test/kotlin/com/anjo/statisticservice/plugin/TemperaturePluginTest.kt` to `plugin/rpi_temperature_api/src/test/kotlin/com/anjo/statisticservice/plugin/TemperaturePluginTest.kt`, updating it to construct against `TemperatureStatsCollectorService`
- [X] T031 [P] [US1] Move `src/test/kotlin/com/anjo/statisticservice/service/exposer/TemperatureStatsExposerServiceTest.kt` to `plugin/rpi_temperature_api/src/test/kotlin/com/anjo/statisticservice/service/exposer/TemperatureStatsExposerServiceTest.kt`
- [X] T032 [P] [US1] Extract the temperature assertions from `src/test/kotlin/com/anjo/statisticservice/validation/ValidationRequestBodyFuncsTest.kt` into `plugin/rpi_temperature_api/src/test/kotlin/com/anjo/statisticservice/validation/TemperatureValidationTest.kt`, then delete the now-empty `ValidationRequestBodyFuncsTest.kt`

### Core cleanup + rewiring

- [X] T033 [US1] Delete `src/main/kotlin/com/anjo/statisticservice/service/StatsCollectorService.kt` and `src/test/kotlin/com/anjo/statisticservice/service/StatsCollectorServiceTest.kt` (logic fully split into T017/T027) (depends on T017, T027)
- [X] T034 [US1] Delete the now-empty `src/main/kotlin/com/anjo/statisticservice/service/exposer/` directory and `src/main/kotlin/com/anjo/statisticservice/utils/DbKeyConstants.kt` (depends on T014, T018, T024, T028)
- [X] T035 [US1] Update `src/main/kotlin/com/anjo/statisticservice/di/DependencyInjection.kt` to `provide`/import `EpidemicStatsCollectorService`, `EpidemicStatsExposerService`, `EpidemicPlugin` from `com.anjo.statisticservice.service`/`.service.exposer`/`.plugin` (now resolved via the `rpi_epidemic_api` module dependency) and the temperature equivalents, dropping any reference to the deleted `StatsCollectorService` (depends on T019, T029, T033)
- [X] T036 [US1] Split `src/test/kotlin/com/anjo/statisticservice/utils/ApplicationConstantsKeyTest.kt` into `plugin/rpi_epidemic_api/src/test/kotlin/com/anjo/statisticservice/utils/EpidemicKeyConstantsTest.kt` (asserts `ApplicationConstants.getKey("epidemic", ...)` == `EpidemicKeyConstants.getEpidemicKey(...)`) and `plugin/rpi_temperature_api/src/test/kotlin/com/anjo/statisticservice/utils/TemperatureKeyConstantsTest.kt` (same for temperature); delete the original file (depends on T013, T023)
- [X] T037 [US1] Run `./gradlew build test` per `quickstart.md` — confirm `CollectStatisticRoutesTest` and `StatsEpidemicExposerRoutingTest` pass unchanged and no other test regresses (depends on T035, T036)

**Checkpoint**: epidemic and temperature fully live in their own modules; `./gradlew build test` green; core has zero epidemic/temperature-specific code left outside `DependencyInjection.kt`'s registration lines.

---

## Phase 4: User Story 2 - A new data-source plugin ships as its own package (Priority: P2)

**Goal**: prove the module split actually gives build isolation and a single core registration point — this is a verification pass over Phase 3's work, not new logic.

**Independent Test**: build each plugin module in isolation; grep core for stray plugin-class references.

- [X] T038 [P] [US2] Run `./gradlew :plugin:rpi_epidemic_api:build` standalone — confirm it succeeds using only `:plugin:plugin-api` as a project dependency (per `contracts/module-boundaries.md`)
- [X] T039 [P] [US2] Run `./gradlew :plugin:rpi_temperature_api:build` standalone — same check
- [X] T040 [US2] Run `grep -rn "com.anjo.statisticservice.di\|StatsRepositoryRedisImpl\|RedisConfig\|RedisClientProvider" plugin/` — must return zero hits; fix any hit found by removing the stray core reference
- [X] T041 [US2] Run `grep -rln "EpidemicPlugin\|TemperaturePlugin" src/main/kotlin` — must return only `src/main/kotlin/com/anjo/statisticservice/di/DependencyInjection.kt`; fix any other hit found

**Checkpoint**: SC-002/SC-003 verified — plugin modules are genuinely independent build units, core names concrete plugins in exactly one file.

---

## Phase 5: User Story 3 - A stub third-party-style plugin proves the mechanism (Priority: P3)

**Goal**: a placeholder `home_assistant_api` module registers and resolves without touching epidemic/temperature code.

**Independent Test**: service starts with the stub registered; its expose route responds; removing the stub module doesn't affect the other two plugins.

- [X] T042 [US3] Create `plugin/home_assistant_api/src/main/kotlin/com/anjo/statisticservice/plugin/HomeAssistantPlugin.kt` implementing `StatPlugin` with `id = "home-assistant"`, no `collect()` override, `expose()` returning a `PluginRouteSet` with one `GET "/"` route responding `200 OK` with an empty JSON body
- [X] T043 [P] [US3] Create `plugin/home_assistant_api/src/test/kotlin/com/anjo/statisticservice/plugin/HomeAssistantPluginTest.kt` — asserts `id == "home-assistant"` and `expose()` returns a `PluginRouteSet` that responds 200 with an empty body
- [X] T044 [US3] Add `HomeAssistantPlugin` to `src/main/kotlin/com/anjo/statisticservice/di/DependencyInjection.kt`'s `provide` block and to the `PluginRegistry(listOf(...))` construction (depends on T042)
- [X] T045 [US3] Run `./gradlew build test` and manually `curl http://localhost:8080/api/v1/stats/expose/home-assistant/` per `quickstart.md` — confirm 200 response and zero effect on epidemic/temperature endpoints (depends on T044)
- [X] T046 [P] [US3] Run `./gradlew :plugin:home_assistant_api:build` standalone — confirm it succeeds using only `:plugin:plugin-api` as a project dependency, closing SC-002's "all three packages build independently" requirement (depends on T042)
- [X] T047 [US3] Re-run `grep -rln "EpidemicPlugin\|TemperaturePlugin\|HomeAssistantPlugin" src/main/kotlin` now that all 3 plugins are registered — must still return only `DependencyInjection.kt`; this re-verifies SC-003 in the final state, since T041's Phase-4 check ran before `HomeAssistantPlugin` existed (depends on T044)
- [X] T048 [US3] Comment out the `implementation(project(":plugin:home_assistant_api"))` line in root `build.gradle.kts` and the `HomeAssistantPlugin` line in `DependencyInjection.kt`'s registration list, re-run `./gradlew build test`, confirm `CollectStatisticRoutesTest`/`StatsEpidemicExposerRoutingTest` still pass unchanged (proves FR-009), then restore both lines (depends on T044, T045)

**Checkpoint**: all three user stories satisfied — epidemic/temperature unchanged, module isolation proven for all 3 plugin packages (SC-002), zero stray plugin references in core in the final state (SC-003), stub is provably optional (FR-009).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately
- **Foundational (Phase 2)**: depends on Phase 1 — BLOCKS all user stories (nothing compiles against `plugin-api` until it exists)
- **User Story 1 (Phase 3)**: depends on Phase 2 — this is the MVP; must complete before Phase 4/5 (Phase 4 verifies Phase 3's output, Phase 5's DI wiring change touches the same file Phase 3 finalizes in T035)
- **User Story 2 (Phase 4)**: depends on Phase 3 completing (it's a verification pass over the Phase 3 move, not independent new code)
- **User Story 3 (Phase 5)**: depends on Phase 2 only for `plugin-api`, but T044 touches the same `DependencyInjection.kt` line-area as T035 — sequence after Phase 3 to avoid merge churn on one file

### Within Phase 3

- Epidemic tasks (T013-T022) and temperature tasks (T023-T032) are independent of each other — can run as two parallel tracks
- Core cleanup (T033-T037) depends on both tracks finishing

### Parallel Opportunities

- T002-T005 (module `build.gradle.kts` files) — different files, run together
- T007-T009 (plugin-api type moves) — different files, run together
- T013-T016 and T023-T026 (epidemic vs temperature constants/models/validation) — two independent tracks, fully parallel
- T020-T022 and T030-T032 (test moves) — parallel within and across the two tracks
- T038-T039 (standalone module builds) — parallel
- T046 (home_assistant_api standalone build) — parallel with T047 (both read-only checks against the finished Phase 5 state)

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1)
2. **STOP and VALIDATE**: `./gradlew build test` green, quickstart.md manual checks pass
3. This alone delivers the roadmap's Phase 2.5 core value — plugins as separate packages, zero behavior change

### Incremental Delivery

1. Setup + Foundational → `plugin-api` exists, nothing else compiles yet
2. US1 → epidemic/temperature fully relocated, app works exactly as before (MVP)
3. US2 → confirms the isolation property actually holds (cheap, mostly grep/build checks)
4. US3 → stub module proves a third plugin slots in cleanly, unblocking real Phase 3 (Home Assistant) later
