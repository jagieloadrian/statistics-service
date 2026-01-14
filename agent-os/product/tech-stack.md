# Tech Stack — StatisticsService

## Backend
- Kotlin + Ktor (HTTP + WebSocket server)
- Gradle Kotlin DSL for builds

## Data Storage
- Redis (Streams) for ingestion and short/medium-term time-series retention
- PostgreSQL or ClickHouse as future options for analytics/long-term storage
- Redis for caching and indices

## API & Contracts
- JSON over HTTP for ingestion and REST APIs
- WebSocket for live feeds (Optional)
- DTOs shared in Kotlin Multiplatform for UI and backend models (as shown in `.docs`)

## Infrastructure & Local Dev
- Docker + docker-compose for local dev and one-click demos
- Implement orchestrator like minikube or k8s
- GitHub Actions for CI (builds, tests)

## Observability
- Prometheus metrics + Grafana dashboards for service metrics
- Centralized logs via Loki/ELK in non-MVP environments

## Testing
- Unit tests with JUnit, integration tests for endpoints, contract tests validating `.docs` shapes

## Notes
- Keep components modular so storage backends and UIs can be swapped without changing contracts.
- Security: local network relaxed for MVP, plan SSO/RBAC for enterprise usage later.

