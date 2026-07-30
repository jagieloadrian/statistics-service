# Quickstart: Validate Plugin Module Extraction (Phase 2.5)

## Prerequisites

- Local Redis running (`.devops/redis-compose` — `docker compose up`), since
  validation exercises real collect/expose behavior through
  `StatsRepositoryRedisImpl`.
- `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64` (per prior phases' notes).

## Build each module independently (SC-002)

```bash
./gradlew :plugin:plugin-api:build
./gradlew :plugin:rpi_epidemic_api:build
./gradlew :plugin:rpi_temperature_api:build
./gradlew :plugin:home_assistant_api:build
```

Expected: each succeeds on its own — a plugin module failing here because it
reaches for a core class is exactly what this phase prevents (see
`contracts/module-boundaries.md`).

## Build the whole app

```bash
./gradlew build
```

Expected: core links all 4 plugin modules and compiles clean.

## Run tests

```bash
./gradlew test
```

Expected:
- Existing collect/expose routing tests (generic `/collect/{pluginId}`,
  `/expose/{pluginId}/**`) pass unchanged — proves SC-001.
- Relocated `EpidemicPluginTest`/`TemperaturePluginTest` (now living in their
  own module's test source set) pass with the same assertions.
- A new minimal test for `HomeAssistantPlugin` (registers, resolves by id,
  `expose()` responds) passes — proves User Story 3.

## Manual sanity check (optional)

With the app running, repeat the exact same requests as Phase 2's quickstart
(`.docs/RPI_Epidemic_Contract.md` / `.docs/RPI_Temperature_Contract.md`
payloads to `/api/v1/stats/collect/epidemic` and `/collect/temperature`, and
the existing expose endpoints) — response shape/status must be byte-for-byte
identical to before the module split.

Also check the stub plugin resolves:

```bash
curl -i http://localhost:8080/api/v1/stats/expose/home-assistant/
```

Expected: `200 OK`, placeholder body, no effect on epidemic/temperature.

## Verify the dependency boundary (SC-003)

```bash
grep -rn "com.anjo.statisticservice.di\|StatsRepositoryRedisImpl\|RedisConfig\|RedisClientProvider" plugin/ || echo "clean"
grep -rln "EpidemicPlugin\|TemperaturePlugin\|HomeAssistantPlugin" src/main/kotlin
```

Expected: first command prints `clean`; second command lists only
`DependencyInjection.kt`.
