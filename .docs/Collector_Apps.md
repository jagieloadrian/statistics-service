# Collector Apps

A collector is a small program of your own that fetches or computes data and
pushes it to the platform. The platform never loads your code (constitution I);
the collector talks to it only through the ingest API or the configured broker
(Kafka or NATS JetStream, see the end of this page), so it can be written in
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
| 409 | the source takes data over pub/sub, publish to the broker instead |
| 429 / 503 | wait `Retry-After` seconds, then resend the same records |
| network error / 5xx | retry later; buffer locally if gaps matter |

## Publishing to a broker (pub/sub)

When the platform runs with a broker (`BROKER=kafka` or `BROKER=jetstream`),
a collector can publish instead of calling HTTP; the broker buffers messages
while the platform is down. Create the source with
`"channel": {"type": "pubsub"}` (the source editor shows its address); HTTP
push to it then answers 409.

| | Kafka | NATS JetStream |
|-|-------|----------------|
| Where | topic `stats.ingest` (`KAFKA_TOPIC`) | subject `stats.ingest.<sourceId>` (`NATS_SUBJECT_PREFIX`) |
| Key | the source id | — (it is the last subject token) |
| Header | `Authorization: Bearer <source token>` | `Authorization: Bearer <source token>` |
| Value | the same JSON body as for HTTP push (native records or the device payload) | same |

- Delivery is at least once: a `pubsub` source must have a mapping with an
  `identity` in every rule (saving it otherwise answers 400), so a redelivered
  message is not stored twice. Native records without a mapping are push-only.
- There is no reply. Rejected messages (wrong token, unknown source, mapping
  errors, paused source) are dropped and show up in
  `GET /api/v2/sources/{sourceId}/status`; check it after setting up.
- Homelab NATS: the `collector` user may publish `stats.ingest.>` only. A plain
  (core) publish needs nothing else; a JetStream publish that waits for the
  stream's ack needs the inbox prefix `_INBOX_collector`
  (`nats --inbox-prefix _INBOX_collector pub --jetstream …`, jnats
  `Options.builder().inboxPrefix("_INBOX_collector")`).

```bash
# Kafka (key and header: parse.key / parse.headers, tab separated)
printf 'Authorization:Bearer %s\t%s\t%s\n' "$SOURCE_TOKEN" "$SOURCE_ID" "$PAYLOAD" |
  kafka-console-producer.sh --bootstrap-server "$KAFKA" --topic stats.ingest \
    --reader-property parse.key=true --reader-property parse.headers=true

# NATS JetStream
nats pub "stats.ingest.$SOURCE_ID" "$PAYLOAD" -H "Authorization:Bearer $SOURCE_TOKEN" -s "$NATS_URL"
```
