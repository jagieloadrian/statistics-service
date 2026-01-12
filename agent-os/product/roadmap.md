# Product Roadmap — StatisticsService

1. [ ] Ingestion API & contracts compliance — Implement POST endpoints for `/v1/api/stats/collect/epidemic` and `/v1/api/stats/collect/temperature` per `.docs` and add validation & tests. `M`
2. [ ] Redis Streams storage & indexing — Persist entries to streams and maintain run/device indices for queries. `M`
3. [ ] UI-facing endpoints — Implement list-runs, run timeline, run summary, temperature series, and temperature summary endpoints. `M`
4. [ ] Live feed support — Optional WebSocket endpoints for run and temperature live updates. `S`
5. [ ] Local deployment — Provide docker-compose and startup scripts for backend, Redis, and demo UI. `S`
6. [ ] Demo UI & integration tests — Minimal web UI consuming the UI contracts + integration tests for end-to-end flows. `M`
7. [ ] Exports & tooling — CSV/JSON export, basic CLI tooling for importing/exporting datasets. `S`
8. [ ] Scaling & analytics — Introduce aggregated storage (ClickHouse) and background workers for heavy analysis. `L`

> Notes
> - Order follows dependency: ingestion -> storage -> UI -> deploy -> scale.
> - Each item represents an end-to-end feature: backend + UI + tests where applicable.

