# Collector Apps

A collector is a small program of your own that fetches or computes data and
pushes it to the platform. The platform never loads your code (constitution I);
the collector talks to it only through the ingest API, so it can be written in
any language, run anywhere, and crash without affecting anything else.

Example: [`examples/collector.py`](../examples/collector.py) (Python stdlib only).

## Collector or mapping?

| Situation | Use |
|-----------|-----|
| The device already sends JSON over HTTP | a **source mapping**, no collector ([Device_Ingest.md](Device_Ingest.md)) |
| Data has to be fetched (polling an API, reading a file, scraping) | a collector |
| Values need logic a mapping cannot express (state across payloads, joins, unit fixes, filtering) | a collector |
| A protocol the platform does not speak (MQTT, serial, Modbus, …) | a collector |

## Setup

1. Create the dataset with every measure and dimension the collector will send.
2. Create a source for it with `"mapping": []` (native record format).
3. `POST /api/v2/sources/{id}/credential` and give the token to the collector.

## Sending

`POST /api/v2/ingest/{sourceId}` with `Authorization: Bearer <source token>` and native records:

```json
[ { "ts": "2026-10-27T10:00:00Z", "dims": { "device": "pico-1" }, "vals": { "temperature": 21.4 } } ]
```

- `ts` is optional (arrival time is used); `dims`/`vals` keys must be declared in the dataset.
- One request is all or nothing: any invalid record → 400 with one reason per problem, nothing stored.
- Native records are not deduplicated; send each reading once.

## Errors and retries

| Status | Collector should |
|--------|------------------|
| 200 | carry on |
| 400 | log `details` and drop or fix those records; resending them unchanged fails again |
| 401 / 404 | stop: token rotated or wrong source id |
| 429 / 503 | wait `Retry-After` seconds, then resend the same records |
| network error / 5xx | retry later; buffer locally if gaps matter |
