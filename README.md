# StatisticsService

Backend of the universal statistics platform (spec
[004](specs/004-universal-stats-platform/spec.md)): sources push data, mappings
turn it into records of a dataset, dashboards query them. Everything domain
specific is configuration (JSON definitions), not code. Frontend: the
`Statistics-UI` repo.

## Run

```bash
docker compose -f .devops/postgres-compose/compose.yaml up -d   # PostgreSQL 16, db/user/password "stats"
ADMIN_EMAIL=admin@local ADMIN_PASSWORD='change-me-now' ./gradlew run
```

| Env | Default | Meaning |
|-----|---------|---------|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/stats`, `stats`, `stats` | database; the schema is created on start |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | empty | first admin, created only while there are no users |
| `CORS_HOSTS` | `localhost:8081` | comma-separated frontend hosts |

API: `http://localhost:8080/api/v2`, Swagger UI at `/api/v2/swagger`.

## Load the example definitions

```bash
API=http://localhost:8080/api/v2
TOKEN=$(curl -s $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@local","password":"change-me-now"}' | jq -r .token)
for f in definitions/*.json; do
  curl -s -X POST $API/import -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d @$f; echo
done
curl -s -X POST $API/sources/temperature/credential -H "Authorization: Bearer $TOKEN"   # source token for the device
```

Devices and collector apps: [.docs/Device_Ingest.md](.docs/Device_Ingest.md),
[.docs/Collector_Apps.md](.docs/Collector_Apps.md).

## Build & test

`./gradlew build` runs all tests; they start PostgreSQL through Testcontainers,
so Docker must be running.

## Docs

- Spec, plan, contracts: [specs/004-universal-stats-platform/](specs/004-universal-stats-platform/)
- API contract: [contracts/api-v2.md](specs/004-universal-stats-platform/contracts/api-v2.md)
- Definition format: [contracts/definition-format.md](specs/004-universal-stats-platform/contracts/definition-format.md)
