# Quickstart: Validate the Universal Statistics Platform

End-to-end checks per delivery slice ([plan.md](./plan.md#delivery-slices)).
Shapes: [contracts/api-v2.md](./contracts/api-v2.md),
[contracts/definition-format.md](./contracts/definition-format.md).

## Prerequisites

- Docker, JDK for both Gradle builds, `curl`, `jq`.
- Backend repo: `/home/diether18/IdeaProjects/StatisticsService`;
  frontend repo: `/home/diether18/IdeaProjects/Statistics-UI`.

```bash
docker compose -f .devops/postgres-compose/compose.yaml up -d
ADMIN_EMAIL=admin@local ADMIN_PASSWORD='change-me-now' ./gradlew run
API=http://localhost:8080/api/v2
# from S5 on (before S5 omit the header):
TOKEN=$(curl -s $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@local","password":"change-me-now"}' | jq -r .token)
AUTH="Authorization: Bearer $TOKEN"
```

## §0 S0 — old stack gone

`./gradlew build` green in both repos; `grep -rniE 'epidemic|temperature' src composeApp/src`
finds nothing outside test fixtures (SC-002); `GET /api/v1/stats/expose/temperature/devices` → 404.

## §1 S1 — push source → record → query (US1)

1. `PUT $API/datasets/room-climate` and `PUT $API/sources/room-climate` (flat
   mapping like the temperature example) → 200.
2. `POST $API/sources/room-climate/credential` → save `token`.
3. `POST $API/sources/room-climate/mapping-test` with one sample payload →
   one record, no errors.
4. Send 100 payloads to `POST $API/ingest/room-climate` with
   `Authorization: Bearer <source token>` → each 200 `{"accepted":1}`.
5. `POST $API/query` (`bucket: "raw"`, last 24 h, `groupBy: ["device"]`) →
   100 rows total; each visible < 5 s after its ingest call.

Expected failures: no token → 401; missing `temperature` → 400 with reason,
and the reason appears in `GET $API/sources/room-climate/status` within
1 min (SC-006); re-sending an identical payload → 200 `{"duplicates":1}`.

## §2 S1 — native format (FR-025)

Source with `"mapping": []`; send
`[{"ts":"…","dims":{"device":"x"},"vals":{"temperature":20}}]` → stored;
undeclared key in `vals` → 400.

## §3 S2 — temperature + epidemic as configuration (US2)

```bash
curl -s -X POST $API/import -H "$AUTH" -H 'Content-Type: application/json' -d @definitions/temperature.json
curl -s -X POST $API/import -H "$AUTH" -H 'Content-Type: application/json' -d @definitions/epidemic.json
```

Rotate both source credentials, send the sample payloads from
`.docs/archive/RPI_Temperature_Contract.md` / `RPI_Epidemic_Contract.md`.

- Temperature: dimension values for `device` list first/last seen; query with
  bucket `raw`, `1m`, `1h`, `1d` returns the right point counts; `none` with
  min/avg/max matches values computed by hand from the samples.
- Epidemic: one payload → 4 records (`type` = `total`, `0`, `1`, `2`);
  run summary query returns `peakInfected`, `timeToPeak` (`at_max` by
  `infected`), `finalRecovered`, `finalDead`, `attackRate`.
- Performance (SC-004): seed ~5 M records into one dataset, e.g.
  `INSERT INTO records SELECT 1,'temperature', now() - (i || ' s')::interval,
  jsonb_build_object('device','d'||(i%10)), jsonb_build_object('temperature', 20 + random()*5), NULL, false
  FROM generate_series(1,5000000) i;` then time 8 parallel 30-day `auto`
  queries → all done < 2 s p95. Miss → research D4 upgrade path.

## §4 S3 — generic dashboards in the frontend (US2, US3)

`./gradlew :composeApp:wasmJsBrowserDevelopmentRun` (and `:composeApp:run`
for desktop). Home shows the imported dashboards as tiles. Temperature
dashboard: switching the `device` variable redraws every widget without
reload; a 30-day range shows the applied auto bucket. Delete a measure with
`?force=true` → its widget shows "missing data definition", others still render.

## §5 S4 — editors, export/import (US1, US3)

Fresh installation: create source + dataset + dashboard only through the UI,
including a mapping test, in < 10 min (SC-001). `GET $API/export` → import
into a second empty installation → identical widgets for the same data (SC-007).

## §6 S5 — user panel & access (US4)

Admin creates `editor@local` and `viewer@local`. Editor makes one private
dashboard and one shared with the viewer. Viewer sees only the shared one;
`PUT` on it → 403; any `/sources/**` call → 403 (SC-008, automated in
`AccessControlTest`). Changing time zone in Account changes displayed times
only. Six wrong logins in a minute → 429.

## §7 S6 — RPi devices on the platform

On each device change only the target URL to
`http://<host>:8080/api/v2/ingest/{temperature|epidemic}` and add header
`Authorization: Bearer <source token>` (SC-003). Source status flips from
"waiting for data" to "receiving"; dashboards fill in.

## §8 S7+ — other channels (US5, US6)

Per channel, against a test producer: 1 000 records → 1 000 stored, zero
duplicates after a platform restart mid-stream; stopping the producer flips
status to "silent" after `silentAfterMinutes`. Collector app (US6): a
20-line script pushing native records with a source token; killing it
affects nothing else.
