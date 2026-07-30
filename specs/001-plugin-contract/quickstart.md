# Quickstart: Validate Plugin Contract (Phase 1)

## Prerequisites

- JDK + Gradle wrapper (existing project setup)
- No Redis/Docker needed — this phase's new code (`plugin/` package,
  `ApplicationConstants.getKey`) has no runtime Redis dependency; existing tests
  that do need Redis are unaffected since nothing routes through the new code yet.

## Build

```bash
./gradlew build
```

Expected: compiles clean. `StatPlugin`, `PluginRegistry`, `PluginRouteSet` exist
with zero production callers (SC-004).

## Run tests

```bash
./gradlew test
```

Expected:
- All pre-existing tests pass unchanged (SC-002) — no epidemic/temperature
  collect/expose behavior difference.
- New `PluginRegistryTest`: known id resolves; unknown id throws
  `EmptyDataException`; duplicate id in the registration list fails construction.
- New `ApplicationConstantsKeyTest`: `getKey("epidemic", deviceId, runId)` and
  `getKey("temperature", deviceId, null)` produce output identical to
  `getEpidemicKey`/`getTemperatureKey` for the same inputs (SC-001).

## Manual sanity check (optional)

Start the app as usual (see project `README.md`) and hit the existing endpoints —
response shape/status for `/api/v1/stats/collect/epidemic`,
`/api/v1/stats/collect/temperature`, and the UI-facing expose endpoints must be
unchanged from before this phase. See `.docs/*.md` for expected shapes.
