# Spec Requirements: 2026-01-09-initial-spec

## Initial Description
Product is a backend application, which persist, calculate and prepare data for chart for ui app. In 1st phase use ktor, kotlin, as a web backend app, redis for persist data, it has an endpoint to collect data from microcontrollers, which are collecting data and send into this app. Current apis/contracts which are implemented are in `.docs/` folder.

## Requirements Discussion

### First Round Questions

**Q1:** I assume the MVP should prioritize reliable local ingestion and basic UI visualizations over multi-tenant or enterprise features. Is that correct, or should enterprise features (SSO/RBAC, strict security) be included in MVP?
**Answer:** include enterprise featuires

**Q2:** For ingestion, should the backend strictly follow the JSON shapes in .docs (no variant tolerated), or allow a lenient/transforming ingestion layer that accepts similar payloads and normalizes them?
**Answer:** allow lenient/transforming ingestion layer

**Q3:** For local deployment, is docker-compose the preferred one-click target, or would you rather use a lightweight Kubernetes (minikube/k3d) target instead?
**Answer:** lightweight kubernetes/minikube

**Q4:** For data retention and storage, do you want a simple time-based TTL on Redis Streams for MVP, or an export/archival path to Postgres/ClickHouse from day one?
**Answer:** use redis and export archival data to postgres

**Q5:** What throughput should the system be designed for initially (messages/sec per device and expected concurrent devices) — e.g., tens, hundreds, or thousands of devices?
**Answer:** tens of devices

**Q6:** For the demo UI, should it be minimal (static pages hitting endpoints) or include interactive charts (e.g., Chart.js/D3) and run-time live updates via WebSocket?
**Answer:** should use interactive ui written in kotlin multiplatform app which is created here @~/IdeaProjects/Statistics-UI

**Q7:** Are there any existing repository files or folders implementing similar patterns (ingestion endpoints, stream handling, demo UI) we should reuse? If yes, provide paths or names.
**Answer:** UI app /home/{user}/IdeaProjects/Statistics-UI and microcontrollers project /home/{user}/IdeaProjects/PicoProjectsPythong

### Existing Code to Reference
Similar Features Identified:
- Feature: UI app - Path: `/home/{user}/IdeaProjects/Statistics-UI`
- Feature: Microcontrollers project - Path: `/home/{user}/IdeaProjects/PicoProjectsPythong`
- Repository source path suggested for reuse: `/home/{user}/IdeaProjects/StatisticsService/src`

### Follow-up Questions
No follow-up questions at this time.

## Visual Assets

### Files Provided:
No visual assets provided.

### Visual Insights:
- No visual files found in the visuals folder.

## Requirements Summary

### Functional Requirements
- Implement robust ingestion endpoints for epidemic and temperature payloads with a lenient/transforming ingestion layer that normalizes variants to canonical DTOs.
- Persist raw entries to Redis Streams and maintain indices for runs and devices.
- Provide UI-facing REST and optional WebSocket endpoints per `.docs` for runs, timelines, summaries, and temperature series.
- Support enterprise features (SSO, RBAC) in the product roadmap and design, target inclusion during early iterations.
- Provide a one-click local deployment target for Kubernetes (minikube/k3d) and CI scripts to stand up redis + backend + demo UI. Include docker-compose as a secondary option for simpler setups.
- Export/archival path from Redis to PostgreSQL for longer-term storage/analytics.
- Demo UI is an interactive Kotlin Multiplatform application located at `/home/{user}/IdeaProjects/Statistics-UI` which should be used as the primary UI client for integration and testing.

### Reusability Opportunities
- Reuse existing UI application at `/home/{user}/IdeaProjects/Statistics-UI` for frontend integration and testing.
- Reference microcontroller payload formats and helpers at `/home/{user}/IdeaProjects/PicoProjectsPythong` for ingestion test vectors.
- Follow existing code patterns in `/home/{user}/IdeaProjects/StatisticsService/src` for integration points and service layout.

### Scope Boundaries
**In Scope:**
- Building ingestion endpoints with normalization and persistence to Redis Streams
- Implementing UI-facing endpoints and optional WebSocket live feeds
- Integrating with the Kotlin Multiplatform UI at `/home/{user}/IdeaProjects/Statistics-UI`
- Providing Kubernetes (minikube/k3d) local deployment manifests and scripts, plus docker-compose fallback
- Implementing export/archival path to PostgreSQL
- Basic enterprise features scaffolded (SSO/RBAC integration points) for early inclusion

**Out of Scope:**
- Full-scale analytics migration to ClickHouse (deferred to later roadmap)
- Production-scale multi-tenant orchestration and billing systems
- High-volume throughput optimization beyond 'tens of devices' unless future scaling is requested

### Technical Considerations
- Integration Points: Redis Streams for ingestion; REST/WebSocket APIs for UI; PostgreSQL for archival exports.
- System Constraints: Initial target throughput of tens of devices; design for eventual scaling and pluggable storage backends.
- Technology Preferences: Ktor + Kotlin backend, Kotlin Multiplatform UI, minikube/k3d for local Kubernetes deployments.
- Similar code patterns to follow: existing src tree at `/home/{user}/IdeaProjects/StatisticsService/src`.

