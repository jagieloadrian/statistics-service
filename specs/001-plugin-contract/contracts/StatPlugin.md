# Contract: StatPlugin

Internal Kotlin contract — no external/HTTP surface changes this phase (FR-008).
This is what Phase 2's `EpidemicPlugin`/`TemperaturePlugin` and Phase 3's
`HomeAssistantPlugin` implement against.

```kotlin
package com.anjo.statisticservice.plugin

interface StatPlugin {
    val id: String

    suspend fun collect(raw: JsonElement) {
        // default no-op for expose-only plugins (e.g. Home Assistant, Phase 3);
        // push-based plugins (epidemic, temperature) override this
    }

    suspend fun expose(): PluginRouteSet
}
```

## Registry contract

```kotlin
class PluginRegistry(plugins: List<StatPlugin>) {
    fun resolve(pluginId: String): StatPlugin
    // throws EmptyDataException (404-mapped) if pluginId is not registered
    // throws at construction time if `plugins` contains a duplicate id
}
```

## Guarantees this phase

- `StatPlugin` and `PluginRegistry` compile with **zero production callers** (SC-004) —
  no route, service, or DI wiring references them yet.
- Adding either type introduces **no behavior change** to `/api/v1/stats/collect/*`
  or the existing expose endpoints (FR-008).
- `PluginRegistry(plugins).resolve(id)` for `id` in `{"epidemic", "temperature"}`
  is not exercised by any live code path this phase — the guarantee validated here
  is purely "correctly resolves/rejects when constructed and called directly in a test."
