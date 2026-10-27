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
| Deps | b0b3581 | version catalog `gradle/libs.versions.toml`, Kotlin 2.4.21, Ktor 3.6.0, Gradle 9.8.2 |
| Cleanup | f345b77 | specs 001-003, v1 contract archive and old notes removed |
| Refactor | c0fa477, 25fea6f, 450e935 | layered packages (controller/service/repository/model/db/di/exception), Flyway, Exposed |
| Tests | dabe96c | Kotest (FunSpec) |

`./gradlew build` green, 47 tests (Kotest, Testcontainers Postgres).

Frontend (`Statistics-UI`, branch `004-universal-stats-platform`): US1 b7d59d0, US2 ce0384f,
US3 156efe4, US4 0a3d3d2, README 9c7679b, deps f4dc599, layered packages + `model/` 79b81a0,
Kotest b8e061e, dead code removed + logic out of screens 7e55873. `jvmTest` (13) green;
js/wasm compile again.

## Decisions

- Old v1 API dropped entirely; RPi devices get the new URL + source token (`.docs/Device_Ingest.md`).
- One organisation (`org_id = 1`), built for multi-org. Redis compose removed; the old Redis data
  stays on disk only (`.devops/redis-compose/redis-data`, git-ignored, root-owned).
- No in-process plugins: custom logic lives in collector apps over the ingest API.
- Contract additions during US4: editors read datasets and `/users` (widget builder, share picker);
  `GET /dashboards/{id}/shares`; viewers query only datasets of dashboards they can see.
- Ingest rate limit is a fixed 50 requests/s per source (`ponytail:` note); batches give more records/s.
- Raw queries are capped at 10 000 rows per query, not per series.
- Schema via Flyway (`db/migration/V1__init.sql`, `baselineOnMigrate`: databases from the old
  startup schema are marked V1). SQL through Exposed DSL; analytic measures stay checked SQL text.
- Services own transactions (`db.tx`), repositories run inside them; models only in `model/`.
- KHealth no longer wraps its routes in `hide()`: in Ktor 3.6 that hid the whole routing root
  from OpenAPI.

## Measured

- SC-004 **missed**: 8 parallel 30-day queries on 5 M records, p95 12 s (target 2 s); single query 2.3 s.
- SC-005 met: 480 records/s for 60 s, p95 8 ms.

Details: research.md D4. Not a release gate.

## Next steps

1. Manual UI run of quickstart §4/§5 in the desktop app against a live backend (T078). The sign-in,
   prefs, users and shares screens have not been exercised end to end yet.
2. Follow-up spec for query speed (SC-004): hourly pre-aggregation or TimescaleDB continuous aggregates.
3. Desktop app crashes Xorg (RENDER segfault, Intel+NVIDIA PRIME on X11): uncommitted
   `-Dsun.java2d.xrender=false` in Statistics-UI `composeApp/build.gradle.kts` waits for a manual test.
   Kotest on js/wasm needs the `io.kotest` plugin + KSP (tests compile, not discovered yet).
4. Reprogram the RPi devices (quickstart §7): new URL `/api/v2/ingest/{temperature|epidemic}` + header.
5. US5 channels (pull, Kafka, gRPC) only when a real producer exists.
6. Small UI gaps noted by `ponytail:` comments: the dashboard JSON has no owner, so the Edit button
   shows for editors on other people's dashboards (the server answers 403).
