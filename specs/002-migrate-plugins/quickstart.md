# Quickstart: Validate Plugin Migration (Phase 2)

## Prerequisites

- Local Redis running (`.devops/redis-compose` — `docker compose up`), since
  this phase's tests exercise real collect/expose behavior through the
  existing `StatsRepository`, unlike Phase 1's pure-unit-test scope.
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (per Phase 1 notes — local
  default JDK21 install is JRE-only, no `javac`).

## Build

```bash
./gradlew build
```

Expected: compiles clean with `EpidemicPlugin`, `TemperaturePlugin`,
`PluginValidationException`, and the new generic routing file in place.

## Run tests

```bash
./gradlew test
```

Expected:
- `CollectStatisticRoutesTest` and `StatsEpidemicExposerRoutingTest`
  (re-pointed at the generic `/collect/{pluginId}` and `/expose/{pluginId}/**`
  routes) pass with unchanged assertions — proves SC-001/FR-007.
- New `EpidemicPluginTest`/`TemperaturePluginTest` pass — thin coverage of the
  collect delegation + validation-exception path.
- No regressions in any other existing test.

## Manual sanity check (optional)

With the app running, `POST` the same epidemic/temperature payloads used in
`.docs/RPI_Epidemic_Contract.md` / `.docs/RPI_Temperature_Contract.md` to
`/api/v1/stats/collect/epidemic` and `/collect/temperature`, and `GET` the
existing expose endpoints — response shape/status must be unchanged from
before this phase.

## After FR-008 deletion step

Re-run `./gradlew build test` once more after the old
`StatsCollectorRouting.kt`/`StatsExposerRouting.kt`/`StatsExposerFacade.kt`
are deleted — same expectations as above (SC-002/SC-003), plus:

```bash
grep -rn "StatsExposerFacade\|StatsCollectorRouting\|StatsExposerRouting" src/main/kotlin src/test/kotlin
```

Expected: zero hits (SC-003).
