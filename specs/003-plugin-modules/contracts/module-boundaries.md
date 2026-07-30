# Contract: Gradle module dependency boundaries

This phase's "contract" isn't an HTTP surface (that's unchanged, see
`.docs/*_Contract.md` and Statistics-UI's `ApiUrls.kt` — still the acceptance
bar per FR-006) — it's the *build-time dependency graph* the spec's FR-001/
FR-004 require. This is what CI/code review checks going forward.

## Allowed dependency directions

```text
:plugin:plugin-api            <- depends on nothing project-local
:plugin:rpi_epidemic_api      <- depends on :plugin:plugin-api only
:plugin:rpi_temperature_api   <- depends on :plugin:plugin-api only
:plugin:home_assistant_api    <- depends on :plugin:plugin-api only
core (root project)           <- depends on :plugin:plugin-api
                                          + :plugin:rpi_epidemic_api
                                          + :plugin:rpi_temperature_api
                                          + :plugin:home_assistant_api
```

**Forbidden**:
- Any `:plugin:*` module depending on the root project — a plugin must never
  import a core-only class (`StatsRepositoryRedisImpl`, `RedisConfig`,
  `RedisClientProvider`, `DependencyInjection`, any core routing file).
- Any `:plugin:*` module depending on a sibling `:plugin:*` module —
  `rpi_epidemic_api` and `rpi_temperature_api` must not know about each
  other; `home_assistant_api` must not know about either.

## How this is verified

- **Build-level**: `./gradlew :plugin:rpi_epidemic_api:build` and
  `./gradlew :plugin:rpi_temperature_api:build` and
  `./gradlew :plugin:home_assistant_api:build` each succeed run in isolation
  (SC-002) — if a plugin module accidentally referenced a core class, this
  fails to compile since core isn't on that module's classpath at all.
- **Grep-level** (cheap, no tooling needed): after the move,
  `grep -rn "com.anjo.statisticservice.di\|StatsRepositoryRedisImpl\|RedisConfig\|RedisClientProvider" plugin/` must return zero hits.
- **Registration-point check** (SC-003):
  `grep -rln "EpidemicPlugin\|TemperaturePlugin\|HomeAssistantPlugin" src/main/kotlin`
  must return exactly `DependencyInjection.kt` (or whichever single file ends
  up owning the registration list).

## Plugin ID contract (unchanged from Phase 2)

| Plugin | `id` | Module |
|---|---|---|
| Epidemic | `"epidemic"` | `:plugin:rpi_epidemic_api` |
| Temperature | `"temperature"` | `:plugin:rpi_temperature_api` |
| Home Assistant (stub) | `"home-assistant"` | `:plugin:home_assistant_api` |

These ids drive `/api/v1/stats/collect/{pluginId}` and
`/api/v1/stats/expose/{pluginId}/**` unchanged — moving modules does not
change a single id or route shape.
