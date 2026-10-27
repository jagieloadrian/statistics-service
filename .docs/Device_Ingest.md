# Device Ingest

How a device or collector app sends data to the platform. Replaces the archived
`archive/RPI_*_Contract.md` files; full API: `specs/004-universal-stats-platform/contracts/api-v2.md`.

## Setup (admin, once per source)

1. Create the dataset and the source (or import `definitions/temperature.json` /
   `definitions/epidemic.json`).
2. `POST /api/v2/sources/{sourceId}/credential` → `{"token": "…"}`. The token is shown
   once; rotating it invalidates the previous one immediately.

## Sending

```
POST http://<host>:8080/api/v2/ingest/{sourceId}
Authorization: Bearer <source token>
Content-Type: application/json
```

Body is either:

- **the device's own payload, unchanged**: the source mapping turns it into records.
  For the RPi devices this is the same JSON as before; only the URL and the header change
  (`temperature` → `/api/v2/ingest/temperature`, `epidemic` → `/api/v2/ingest/epidemic`);
- **native records** when the source has no mapping, one object or an array:
  `[{"ts": "2026-10-27T10:00:00Z", "dims": {"device": "pico-1"}, "vals": {"temperature": 21.4}}]`
  (`ts` optional, arrival time used).

Timestamps: ISO-8601 instant, or a local date-time read as UTC (`2026-01-05T15:38:14`).

## Responses

| Status | Meaning | Device should |
|--------|---------|---------------|
| 200 `{"accepted": n, "duplicates": m}` | stored; a resend of the same reading counts as duplicate | carry on |
| 400 `{"details": [ … ]}` | payload rejected, nothing stored; one reason per failed field | fix the payload, do not retry as is |
| 401 | missing, wrong or rotated token | stop, needs a new token |
| 404 | unknown source id | stop, check the URL |
| 429 + `Retry-After` | over 50 requests/s for this source | wait `Retry-After` seconds |
| 503 + `Retry-After` | source paused by an admin | retry later |

Rejections are visible to admins in `GET /api/v2/sources/{sourceId}/status`.

Data that needs fetching or custom logic before it can be sent: see [Collector_Apps.md](Collector_Apps.md).
