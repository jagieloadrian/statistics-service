# Phase 1 Data Model: Plugin Contract

Not a persistence schema change (Redis Streams/keys are unchanged, FR-007) — this
documents the new in-process types.

## StatPlugin (interface)

| Member | Type | Notes |
|---|---|---|
| `id` | `String` | stable, unique plugin identifier — e.g. `"epidemic"`, `"temperature"` (matches the `pluginId` segment of the future `/stats/collect/{pluginId}` route from Phase 2, and the prefix already embedded in existing Redis keys) |
| `collect(raw: JsonElement)` | `suspend fun` | present on the interface; push-based plugins implement it, expose-only plugins may no-op (see research.md) |
| `expose()` | `suspend fun (): PluginRouteSet` | plugin's UI-facing read behavior; `PluginRouteSet` shape is a minimal placeholder in Phase 1 — real content added when Phase 2 wires actual routes through it |

**Validation rule**: `id` MUST be non-blank and unique across the registration list (FR-005).

## PluginRegistry

| Member | Type | Notes |
|---|---|---|
| backing map | `Map<String, StatPlugin>` | built once at startup from an explicit `List<StatPlugin>` (FR-003) |
| `resolve(pluginId: String): StatPlugin` | function | returns the matching plugin or throws `EmptyDataException` (FR-004, maps to HTTP 404) |

**Lifecycle**: built once during application startup (DI wiring); immutable afterward.
Construction fails fast (throws) on a duplicate `id` in the input list (FR-005) —
no silent overwrite, no partial registry served to callers (Edge Cases).

## Plugin storage key (generic key builder)

Not an entity with identity — a pure function.

| Input | Type | Notes |
|---|---|---|
| `pluginId` | `String` | e.g. `"epidemic"`, `"temperature"` |
| `deviceId` | `String` | existing device identifier |
| `runId` | `String?` | optional — temperature currently has no run id (device-only key) |

**Output**: `String` key, required to equal today's output byte-for-byte:
- `getKey("epidemic", deviceId, runId)` == `"epidemic:device:$deviceId:run:$runId"` (matches current `getEpidemicKey`)
- `getKey("temperature", deviceId, null)` == `"temperature:device:$deviceId:run"` (matches current `getTemperatureKey` — note: literal `"run"` suffix, no id, since temperature has no run concept today)
