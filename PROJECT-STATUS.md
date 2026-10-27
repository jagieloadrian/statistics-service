# Project Status

Updated 2026-10-27. Feature: [spec 004 universal stats platform](specs/004-universal-stats-platform/spec.md)
(tasks: [tasks.md](specs/004-universal-stats-platform/tasks.md)).

## Completed

Backend (this repo, branch `feature-2/update-ktor-plugin`):

| Slice | Commit | Content |
|-------|--------|---------|
| S0 | 9735ec1 | plugin modules, v1 API, Redis stack removed |
| Foundation | a603ad2 | Postgres schema, `Db`, definition store |
| US1 | e2abdb1 | push ingest, mapper, record store + query, source status, v2 API |
| US2 | 081b3d3 | calculated measures (`Expr`), `definitions/temperature.json` / `epidemic.json` |
| US3 | fbfb33a | export/import, dataset reference guard (409, `?force=true`) |
| US4 | 74bac67 | accounts, roles, bearer tokens, dashboard visibility + shares |
| US6 | a8c442b | `examples/collector.py`, `.docs/Collector_Apps.md` |
| Polish | c9fc443 | README, OpenAPI summary test, performance numbers |

`./gradlew build` green, 47 tests (Testcontainers Postgres).

Frontend (`Statistics-UI`, branch `004-universal-stats-platform`): US1 b7d59d0, US2 ce0384f,
US3 156efe4, US4 0a3d3d2, README 9c7679b. `compileKotlinJvm` + `jvmTest` (13) green.

## Decisions

- Old v1 API dropped entirely; RPi devices get the new URL + source token (`.docs/Device_Ingest.md`).
- One organisation (`org_id = 1`), built for multi-org; Redis kept only as an untouched archive.
- No in-process plugins: custom logic lives in collector apps over the ingest API.
- Contract additions during US4: editors read datasets and `/users` (widget builder, share picker);
  `GET /dashboards/{id}/shares`; viewers query only datasets of dashboards they can see.
- Ingest rate limit is a fixed 50 requests/s per source (`ponytail:` note); batches give more records/s.
- Raw queries are capped at 10 000 rows per query, not per series.

## Measured

- SC-004 **missed**: 8 parallel 30-day queries on 5 M records, p95 12 s (target 2 s); single query 2.3 s.
- SC-005 met: 480 records/s for 60 s, p95 8 ms.

Details: research.md D4. Not a release gate.

## Next steps

1. Manual UI run of quickstart §4/§5 in the desktop app against a live backend (T078). The sign-in,
   prefs, users and shares screens have not been exercised end to end yet.
2. Follow-up spec for query speed (SC-004): hourly pre-aggregation or TimescaleDB continuous aggregates.
3. Web build blocked by KT-82395 (Kotlin 2.3.20-Beta2 "No file found for source null"); retry on
   the next Kotlin release.
4. Reprogram the RPi devices (quickstart §7): new URL `/api/v2/ingest/{temperature|epidemic}` + header.
5. US5 channels (pull, Kafka, gRPC) only when a real producer exists.
6. Small UI gaps noted by `ponytail:` comments: the dashboard JSON has no owner, so the Edit button
   shows for editors on other people's dashboards (the server answers 403).
