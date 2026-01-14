# Product Mission — StatisticsService

## Pitch
StatisticsService is a lightweight backend platform that ingests telemetry from hobbyist microcontrollers (ESP32, RPI Pico), persists raw time-series and run-based simulation data, computes chart-ready statistics, and exposes stable UI-facing APIs for web and desktop clients. It enables local, low-friction deployment for tech-enthusiasts and developers who want to collect, visualize and analyze in-network sensor and simulation data.

## Users

### Primary Customers
- Tech enthusiasts / hobbyists: run small fleets of microcontrollers at home and want simple dashboards and exports.
- Developers / integrators: prototype sensors, extend data pipelines, and integrate with BI tools.

### User Persona — Home Lab Hobbyist
- Role: Home tinkerer who runs ESP32 or RPI Pico devices to gather environmental or simulation data.
- Context: Occasional deployments on local Wi‑Fi, wants simple one-click local demo and a UI to inspect runs and timeseries.
- Pain Points: Complex infra, unreliable ingestion, and opaque data formats.
- Goals: Reliable local ingestion, clear visualizations, easy export and reproducible demos.

## The Problem
Collecting, persisting and producing reliable, consistent time-series and run-based statistics from small devices is non-trivial for hobbyists: devices may produce varied JSON payloads, storage/backfill is error-prone, and UIs require sanitized, well-aggregated datasets.

Our solution: Provide a small, documented backend that accepts device contracts defined in `.docs/`, stores entries in Redis Streams (or pluggable storage), aggregates data for UI consumption, and ships minimal local deployment tooling so users can stand everything up quickly.

## Differentiators
- Local-first, minimal-ops approach: targeted at in-network deployments and low-friction demos rather than cloud-only SaaS.
- Clear, versioned API contracts in repository (.docs) so devices and UIs can be decoupled and evolve safely.
- Modular storage model that allows swapping Redis for a columnar store later without breaking clients.

## Key Features
### Core Features (MVP)
- **Ingestion API:** Robust endpoints to collect epidemic and temperature payloads (contracts in .docs).
- **Time-series storage:** Redis Streams based persistent ingestion with indexing for runs/devices.
- **UI-facing APIs:** Endpoints for listing runs, retrieving timelines, summaries, temperature series and summaries, plus optional WebSocket live feeds.
- **Local deploy:** docker-compose or script to launch backend + Redis + demo UI.

### Collaboration Features
- **CSV/JSON export** for runs and time windows to enable offline analysis.
- **Versioned contract docs** to coordinate device firmware and UI releases.

### Advanced Features (post-MVP)
- **Aggregations & analytics:** Bucketed aggregations, anomaly detection and pre-computed reports (migrateable to ClickHouse).
- **Multi-tenant / RBAC & SSO** for larger deployments.

## Success Metrics
- 99%+ persisted ingestion for test message sets
- Demo UI can list and visualize runs and temperature series within 5s of startup
- One-click local deployment that stands up services reliably for new users

