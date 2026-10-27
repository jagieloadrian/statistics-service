# Contract: Definition Format v1

JSON documents for datasets, sources and dashboards. Same shape in the CRUD
API ([api-v2.md](./api-v2.md)) and in export/import bundles. Every document
carries `"version": 1`; a breaking change bumps it (constitution II).
Names (`id`, measure, dimension, variable) match `^[A-Za-z_][A-Za-z0-9_-]{0,62}$`
(ids may contain `-`; measure/dimension names may not).

## Bundle (export / import)

```json
{ "version": 1, "datasets": [ … ], "sources": [ … ], "dashboards": [ … ] }
```

Import upserts by `(kind, id)`; secrets (source credentials) and users are
never part of a bundle.

## Dataset

```json
{
  "version": 1, "kind": "dataset", "id": "temperature", "label": "Temperature",
  "measures": [
    { "name": "temperature", "type": "number", "unit": "°C", "label": "Temperature" },
    { "name": "humidity",    "type": "number", "unit": "%",  "label": "Humidity" }
  ],
  "dimensions": [ { "name": "device", "label": "Device" } ],
  "calculated": { },
  "retentionDays": null
}
```

## Source

```json
{
  "version": 1, "kind": "source", "id": "temperature", "label": "RPi temperature",
  "channel": { "type": "push" },
  "paused": false, "rateLimitPerSecond": 50, "silentAfterMinutes": 10,
  "dataset": "temperature",
  "mapping": [
    {
      "timestamp": { "path": "timestamp", "format": "iso", "maxAgeHours": 24 },
      "identity": ["deviceId", "timestamp"],
      "dims": { "device": { "path": "deviceId", "notBlank": true } },
      "vals": {
        "temperature": { "path": "temperature", "required": true },
        "humidity":    { "path": "humidity", "required": false }
      }
    }
  ]
}
```

### Native record format (no mapping, FR-025)

`"mapping": []` (or omitted) → the body must already be one record or an array:

```json
[ { "ts": "2026-10-27T10:00:00Z", "dims": { "device": "pico-1" }, "vals": { "temperature": 21.4 } } ]
```

`ts` optional (arrival time used), `dims`/`vals` keys must be declared in the
dataset, values cast and validated like mapped ones; no dedup.

### Mapping rule fields

| Field | Meaning |
|-------|---------|
| `each` | Path to an object (iterate entries) or array (iterate items). Inside the rule, `@key` = entry key / array index, `@.x` = field of the current item; other paths are from the payload root. |
| `timestamp` | `{path, format: "iso" \| "epoch_ms" \| "epoch_s", maxAgeHours?}`. `iso` accepts an instant or a local date-time read as UTC. Missing path value → arrival time, `ts_estimated = true`. |
| `identity` | Paths whose values (plus `@key` in `each` rules) form the dedup key. Omit = no dedup. |
| `dims` | dimension name → `{path}` or `{const}`, optional `notBlank` (default true). Values stored as strings. |
| `vals` | measure name → `{path}` or `{const}`; `required` (default true), `min`, `max`, `scale` (multiplier). Cast to the measure's declared type. |
| `const` | Shorthand: `{ "type": "total" }` adds constant dimension values. |

Rejection reasons are human-readable, one per failed constraint, e.g.
`"vals.temperature: missing at path 'temperature'"`.

### Channel types

| `type` | Settings | Status |
|--------|----------|--------|
| `push` | none (address = `/api/v2/ingest/{id}`) | S1 |
| `pull` | `url`, `intervalSeconds` (≥ 10), `headers` (secret values referenced, not stored inline), `itemsPath?` | when a producer exists |
| `kafka` | `bootstrapServers`, `topic`, `groupId` | when a producer exists |
| `grpc` | none (address = gRPC `Ingest.Write` with `source_id`) | when a producer exists |

## Epidemic (shows `each` + calculated)

```json
{
  "version": 1, "kind": "dataset", "id": "epidemic", "label": "Epidemic simulation",
  "measures": [
    { "name": "generation",  "type": "number" },
    { "name": "susceptible", "type": "number" }, { "name": "exposed",  "type": "number" },
    { "name": "infected",    "type": "number" }, { "name": "recovered","type": "number" },
    { "name": "dead",        "type": "number" }, { "name": "population","type": "number" },
    { "name": "lockdown",    "type": "boolean" }, { "name": "mobility", "type": "number" }
  ],
  "dimensions": [ { "name": "device" }, { "name": "run" }, { "name": "type" } ],
  "calculated": { "attackRate": "(recovered + dead) / population" }
}
```

```json
{
  "version": 1, "kind": "source", "id": "epidemic", "channel": { "type": "push" },
  "dataset": "epidemic",
  "mapping": [
    {
      "timestamp": { "path": "meta.timestamp", "format": "iso" },
      "identity": ["meta.deviceId", "meta.runId", "meta.generation"],
      "const": { "type": "total" },
      "dims": { "device": { "path": "meta.deviceId" }, "run": { "path": "meta.runId" } },
      "vals": {
        "generation": { "path": "meta.generation" },
        "susceptible": { "path": "state.susceptible" }, "exposed": { "path": "state.exposed" },
        "infected": { "path": "state.infected" }, "recovered": { "path": "state.recovered" },
        "dead": { "path": "state.dead" }, "population": { "path": "state.population" },
        "lockdown": { "path": "state.lockdown" }, "mobility": { "path": "state.mobilityMul", "required": false }
      }
    },
    {
      "each": "state.byType",
      "timestamp": { "path": "meta.timestamp", "format": "iso" },
      "identity": ["meta.deviceId", "meta.runId", "meta.generation"],
      "dims": { "device": { "path": "meta.deviceId" }, "run": { "path": "meta.runId" }, "type": { "path": "@key" } },
      "vals": {
        "generation": { "path": "meta.generation" },
        "susceptible": { "path": "@.susceptible" }, "exposed": { "path": "@.exposed" },
        "infected": { "path": "@.infected" }, "recovered": { "path": "@.recovered" },
        "dead": { "path": "@.dead" }
      }
    }
  ]
}
```

## Dashboard

```json
{
  "version": 1, "kind": "dashboard", "id": "epidemic-runs", "title": "Epidemic runs",
  "visibility": "all", "refreshSeconds": 30, "defaultRange": { "last": "P30D" },
  "variables": [
    { "name": "run", "type": "dimension", "dataset": "epidemic", "dimension": "run" },
    { "name": "range", "type": "timeRange" }
  ],
  "widgets": [
    {
      "id": "runs", "title": "Runs", "type": "table", "w": 12, "h": 4,
      "query": {
        "dataset": "epidemic", "filter": { "type": ["total"] }, "groupBy": ["run", "device"],
        "bucket": "none",
        "measures": [
          { "field": "ts", "agg": "min", "as": "startedAt" }, { "field": "ts", "agg": "max", "as": "endedAt" },
          { "field": "population", "agg": "last" }, { "field": "generation", "agg": "max", "as": "duration" },
          { "field": "infected", "agg": "max", "as": "peakInfected" }
        ]
      }
    },
    {
      "id": "timeline", "title": "Run $run", "type": "line", "w": 8, "h": 4,
      "options": { "x": "generation" },
      "query": {
        "dataset": "epidemic", "filter": { "run": ["$run"], "type": ["total"] }, "bucket": "raw",
        "measures": [ { "field": "generation" }, { "field": "susceptible" }, { "field": "infected" },
                      { "field": "recovered" }, { "field": "dead" } ]
      }
    },
    {
      "id": "summary", "title": "Summary", "type": "stat", "w": 4, "h": 4,
      "query": {
        "dataset": "epidemic", "filter": { "run": ["$run"], "type": ["total"] }, "bucket": "none",
        "measures": [
          { "field": "infected", "agg": "max", "as": "peakInfected" },
          { "field": "generation", "agg": "at_max", "by": "infected", "as": "timeToPeak" },
          { "field": "recovered", "agg": "last", "as": "finalRecovered" },
          { "field": "dead", "agg": "last", "as": "finalDead" },
          { "field": "attackRate", "agg": "last" }
        ]
      }
    }
  ]
}
```

### Widget

| Field | Values |
|-------|--------|
| `type` | `line`, `area`, `bar`, `stat`, `gauge`, `table` |
| `w`, `h` | width 1–12 columns, height in rows |
| `query` | [Query request](./api-v2.md#query) without `from`/`to`/`range` (the dashboard time range is applied to every widget at view time); `$variable` placeholders allowed in `filter` values and `bucket` |
| `options` | `x` (measure for x-axis, default `ts`), `unit`, `colors` (per series/measure), `min`, `max`, `thresholds` `[{value, color}]`, `decimals` |

### Variable

| `type` | Fields | Substitutes |
|--------|--------|-------------|
| `dimension` | `dataset`, `dimension`, `multi?` | dimension value(s) |
| `timeRange` | `default?` (ISO-8601 duration, e.g. `P1D`); at most one per dashboard | sets `from`/`to` on every widget query; without it `defaultRange` applies |
| `bucket` | `options` (e.g. `["raw","1m","1h","1d"]`), `default?` | bucket string |
