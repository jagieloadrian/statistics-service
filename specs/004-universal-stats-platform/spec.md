# Feature Specification: Universal, Configuration-Driven Statistics Platform (Respec)

**Feature Branch**: `004-universal-stats-platform`

**Created**: 2026-10-27

**Status**: Draft

**Input**: User description: "Analyze StatisticsService and Statistics-UI and respec them into a universal platform (still separate backend and frontend) that aggregates and displays statistics with a user panel — or propose a ready-made solution if one exists. Skip the domain-specific implementations built so far (epidemic, temperature) in favor of a configurable system where I register a data source with a display template; other backend apps that collect data can be attached via REST/HTTP, gRPC, Kafka, or plugins (if that makes sense)."

> Supersedes the product direction in `user-roadmap.md` (plugin-per-domain) and
> specs 001–003. Build-vs-buy analysis and non-binding technical direction:
> [research.md](./research.md).

## Clarifications

### Session 2026-10-27

- Q: One owner with invited users, or several isolated organizations? → A:
  one owner with invited users in v1, built so isolated organizations can be
  added later without breaking existing definitions or contracts (FR-020).
- Q: Migrate historical Redis data or start empty? → A: start empty; the
  existing store is left untouched as a read-only archive, not shown in the
  new UI (FR-023).
- Q: Collector apps only, or also in-platform extensions? → A: separate
  collector apps only, feeding the platform over the supported channels; no
  in-platform extension mechanism (FR-024).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register a data source and see it on a chart without code (Priority: P1)

As the platform administrator, I register a new data source (name, how data
arrives, how an incoming payload maps onto named measures and dimensions),
point a device or app at it, and add a chart widget that shows the incoming
data — without writing code, rebuilding, or redeploying either the backend or
the frontend.

**Why this priority**: this is the whole point of the respec. Today every new
domain needs a new plugin module in the backend plus new DTOs, screens, and
URLs in the frontend. If this story works, every other story is configuration.

**Independent Test**: on a fresh installation, define a source "room-climate"
receiving pushed JSON `{deviceId, timestamp, temperature, humidity}`, send 100
payloads, create a line-chart widget for `temperature` grouped by `deviceId`
over the last 24 h, and confirm the chart shows all 100 points.

**Acceptance Scenarios**:

1. **Given** no sources exist, **When** the admin defines a source with a
   payload mapping and saves it, **Then** the source is immediately ready to
   accept data and shows status "waiting for data".
2. **Given** a ready source, **When** a payload matching the mapping arrives,
   **Then** it is stored as one or more records and is queryable within 5
   seconds.
3. **Given** a payload missing a required mapped field or with a wrong type,
   **When** it arrives, **Then** it is rejected with a reason the sender can
   read, and the rejection is visible to the admin on the source's status page.
4. **Given** stored records, **When** a user adds a widget choosing dataset,
   measure(s), grouping, time range, and visualization type, **Then** the
   widget renders the data and keeps the choice after a page reload.

---

### User Story 2 - Existing temperature and epidemic views reproduced as pure configuration (Priority: P1)

As the owner of the existing Raspberry Pi devices, I keep them sending exactly
what they send today, and I get temperature and epidemic dashboards equivalent
to today's screens — built only from source/dataset/dashboard definitions, with
no temperature- or epidemic-specific code left in backend or frontend.

**Why this priority**: it is the acceptance test that the platform is actually
generic (two very different shapes: a flat device time series and nested
simulation runs with per-type breakdown), and it protects devices whose
firmware cannot be redeployed in sync with the platform.

**Independent Test**: replay the recorded device payloads from the existing
test fixtures through today's ingestion addresses, import the two provided
dashboard definitions, and compare each widget against today's endpoint output
(device list, series per resolution, summaries, runs list, run timeline, run
summary) for the same data.

**Acceptance Scenarios**:

1. **Given** a device posting to today's ingestion address with today's
   payload, **When** the new platform is live, **Then** the payload is accepted
   with the same success/error responses and lands in the configured dataset.
2. **Given** an epidemic payload with a per-type breakdown (`byType`), **When**
   it is ingested, **Then** each type is queryable as a separate series via a
   dimension (e.g. `type = "0"`), alongside the totals.
3. **Given** the temperature dashboard definition, **When** a user picks a
   device and a resolution (raw / 1 min / 1 h / 1 day), **Then** the series
   and summary values match today's temperature endpoints for the same data.
4. **Given** the epidemic dashboard definition, **When** a user picks a run,
   **Then** the timeline and summary values match today's epidemic endpoints
   for the same run.

---

### User Story 3 - Build and use dashboards with variables (Priority: P2)

As a user, I compose dashboards from widgets (line/area, bar, single stat,
gauge, table), arrange them in a grid, and add dashboard variables (e.g. a
device or run picker, a time range, a resolution) that every widget on the
dashboard follows.

**Why this priority**: turns raw charts into the "display template" the user
asked for; without variables, every device/run would need its own dashboard.

**Independent Test**: create a dashboard with a "device" variable bound to the
distinct values of a dimension and two widgets referencing it; switching the
variable updates both widgets; the dashboard survives export → import on
another installation unchanged.

**Acceptance Scenarios**:

1. **Given** a dimension with values, **When** a user adds a variable bound to
   it, **Then** the picker lists all distinct values with first/last-seen time.
2. **Given** a widget using a variable, **When** the variable changes, **Then**
   the widget re-queries and redraws without a page reload.
3. **Given** a time range too large to draw point-by-point, **When** a widget
   loads, **Then** data is automatically aggregated to a resolution that keeps
   the chart readable, and the applied resolution is shown.
4. **Given** a widget whose dataset or measure was deleted, **When** the
   dashboard opens, **Then** that widget shows a clear "missing data
   definition" state and the rest of the dashboard still works.
5. **Given** a dashboard with an auto-refresh interval, **When** new records
   arrive, **Then** widgets show them on the next refresh.

---

### User Story 4 - User panel: accounts, roles, own and shared dashboards (Priority: P2)

As a user, I sign in, see my dashboards and the ones shared with me, mark
favourites, set my own home dashboard and display preferences (time zone,
units, theme). As an admin, I invite users and assign roles.

**Why this priority**: required by the respec ("panel użytkownika"); also
makes it safe to expose the platform beyond a trusted LAN.

**Independent Test**: admin creates two users (editor, viewer); the editor
creates a private dashboard and shares a second one with the viewer; the viewer
sees only the shared one and cannot edit it or open any source/dataset admin
screen.

**Acceptance Scenarios**:

1. **Given** a signed-out visitor, **When** they open any page other than sign-in,
   **Then** they are redirected to sign-in.
2. **Given** roles admin / editor / viewer, **When** each role uses the
   platform, **Then**: admin manages users, sources, datasets and everything
   else; editor creates and edits dashboards and widgets; viewer only views
   dashboards shared with them or marked shared-with-everyone.
3. **Given** a signed-in user, **When** they change time zone, **Then** all
   timestamps render in that zone while stored data stays unchanged.

---

### User Story 5 - Attach other backend apps via pull, message stream, or RPC (Priority: P3)

As an admin, I attach data that does not arrive by push: the platform polls a
remote HTTP endpoint on a schedule, subscribes to a message-stream topic
(Kafka), or accepts records from other backend apps over an RPC channel
(gRPC) — each with the same mapping rules as push sources.

**Why this priority**: the user explicitly wants these channels, but nothing
uses them today; push covers both existing devices. Deliver each channel when a
concrete producer exists (e.g. the separate Home Assistant project).

**Independent Test**: per channel — configure a source of that type against a
test producer, emit 1 000 records, confirm all 1 000 appear in the dataset
with no duplicates and that stopping the producer flips source status to
"no data for X min".

**Acceptance Scenarios**:

1. **Given** a pull source with a 1-minute interval, **When** the remote
   endpoint is unreachable, **Then** the platform retries with back-off,
   shows the error on the source status, and resumes on recovery without
   duplicating already-stored records.
2. **Given** a message-stream source, **When** the platform restarts, **Then**
   it resumes from the last processed position without loss or duplicates.
3. **Given** an RPC client with a valid source credential, **When** it sends a
   batch, **Then** every record gets an individual accepted/rejected result.

---

### User Story 6 - Custom ingestion logic through separate collector apps (Priority: P3)

As a developer, when a source needs logic that mapping rules cannot express
(custom protocol, auth handshake, stateful transformation — e.g. Home
Assistant), I write a separate collector app in any language that does that
work and feeds the platform over one of the supported channels, instead of
changing the platform.

**Why this priority**: escape hatch only; mapping rules plus the channels in
US1/US5 should cover the known sources.

**Independent Test**: write a minimal collector script that reads from a test
endpoint and pushes records with a source credential; confirm its records
appear, then kill it and confirm the platform keeps working and flags the
source as silent.

**Acceptance Scenarios**:

1. **Given** a running collector app, **When** it sends records, **Then** they
   go through the same validation, storage and status reporting as any source.
2. **Given** a collector app that crashes or hangs, **When** it stops sending,
   **Then** other sources keep ingesting and the admin sees the source as
   "no data for X min".

---

### Edge Cases

- Duplicate delivery of the same record (device retry, stream redelivery): MUST
  be stored once when the source defines an identity (e.g. device + timestamp);
  otherwise stored as received.
- Late or out-of-order records: accepted and placed by their own timestamp,
  not arrival time.
- Records without a timestamp: arrival time is used, flagged as such.
- Payload with extra, unmapped fields: accepted; extras ignored (optionally
  kept as raw for later re-mapping, see FR-008).
- Mapping changed after data exists: adding a measure or dimension applies to
  new records; renaming/removing one requires explicit confirmation and shows
  how many widgets reference it.
- Dimension with very many values (thousands of runs): pickers search and
  page instead of loading all values.
- Burst of traffic from one source: other sources' ingestion and dashboard
  responsiveness are unaffected; excess is rejected with a retry hint, not
  silently dropped.
- Unknown source address / revoked source credential: rejected, logged, never
  stored.
- Time zones: all records stored in UTC; display follows user preference.
- Empty time range: widget shows "no data in range", not an error.

## Requirements *(mandatory)*

### Functional Requirements

**Sources & ingestion**

- **FR-001**: Admins MUST be able to create, edit, pause, resume and delete
  data sources through the user panel and through importable definition files,
  with changes effective without restarting or redeploying anything.
- **FR-002**: The platform MUST support push over HTTP as the baseline
  channel; pull over HTTP (scheduled polling), message stream (Kafka) and RPC
  (gRPC) MUST be supported as additional channel types using the same mapping
  model. (Channel names are user-mandated integration requirements, not
  implementation choices.)
- **FR-003**: Every source MUST have its own credential (except sources the
  admin explicitly marks as open for trusted local devices); credentials MUST
  be rotatable without changing the source's identity.
- **FR-004**: Each source MUST show status: last record time, records
  accepted/rejected in the last hour and day, and the last 50 rejection
  reasons with the offending payload excerpt.
- **FR-005**: Ingestion MUST acknowledge a record only after it is durably
  stored.

**Datasets & mapping**

- **FR-006**: A dataset MUST declare its timestamp, measures (name, numeric /
  boolean / text type, unit, display name) and dimensions (name, display
  name), so any widget can be built from the declaration alone.
- **FR-007**: A source MUST map incoming payloads onto a dataset declaratively:
  pick nested fields by path, rename, convert types and units, set constant
  dimensions, and expand a keyed or listed collection (e.g. `byType`) into one
  record per entry with the key as a dimension.
- **FR-008**: The admin MUST be able to test a mapping against a sample
  payload and see the resulting records before saving.
- **FR-009**: Datasets MUST support an optional retention period after which
  records are deleted; default is keep forever.

**Query & aggregation**

- **FR-010**: Users MUST be able to query any dataset by time range,
  dimension filters, grouping by dimensions, and a time bucket (raw or fixed
  bucket sizes).
- **FR-011**: Aggregations MUST include count, sum, average, min, max, first,
  last, and the timestamp at which min/max occurred (covers today's run
  summaries, e.g. peak infected and when it happened).
- **FR-012**: Users MUST be able to define calculated measures as simple
  arithmetic over other measures of the same dataset (e.g. attack rate =
  (recovered + dead) / population).
- **FR-013**: The platform MUST list distinct values of a dimension with
  first-seen, last-seen and record count (replaces today's devices/runs lists).
- **FR-014**: The query capability MUST be a documented, versioned contract
  usable by clients other than the bundled frontend.

**Visualization & dashboards**

- **FR-015**: Widget types MUST include line/area, bar, single stat, gauge and
  table; each widget is fully defined by dataset, query, visualization type and
  display options (title, colours, units, axis, thresholds).
- **FR-016**: Dashboards MUST support a grid layout, variables (dimension
  value picker, time range, resolution), a default time range and an optional
  auto-refresh interval.
- **FR-017**: Dashboards, widgets, datasets and sources MUST be exportable and
  importable as definition files; import onto an empty installation MUST
  reproduce them exactly (credentials excluded).
- **FR-018**: The frontend MUST contain no domain-specific screens, models or
  addresses; everything it shows is driven by definitions fetched from the
  backend.

**User panel & access**

- **FR-019**: Users MUST sign in before accessing anything except the sign-in
  page; sessions MUST expire after inactivity and on sign-out.
- **FR-020**: The platform MUST provide roles admin, editor and viewer with
  the permissions in US4 scenario 2. An installation serves one owner and the
  users they invite. Isolated organizations are out of scope for v1, but v1
  MUST NOT block them: every source, dataset, dashboard and user belongs to
  the installation's single organization, so adding more organizations later
  requires no change to existing definitions, exports or contracts.
- **FR-021**: Dashboards MUST have visibility private, shared with chosen
  users, or shared with all signed-in users.
- **FR-022**: Each user MUST be able to set favourites, a home dashboard,
  time zone, unit preference and theme.

**Compatibility & migration**

- **FR-023**: Today's device ingestion addresses and payloads for temperature
  and epidemic MUST keep working unchanged, mapped onto their datasets through
  ordinary source definitions. Today's UI-facing read endpoints MAY be removed
  once the new frontend replaces the current screens (the frontend is their
  only consumer). The new platform starts with empty datasets; historical data
  already stored MUST be left untouched as a read-only archive (not migrated,
  not deleted, not shown in the new UI).

**Extensibility**

- **FR-024**: Custom ingestion logic MUST live in separate collector apps
  (any language) that feed the platform over the supported channels using a
  source credential; the platform MUST NOT require code changes or rebuilds to
  accept a new collector, and provides no in-platform extension mechanism.

### Key Entities

- **Source**: where data comes from — name, channel type (push, pull, stream,
  RPC), channel settings, credential, mapping, target dataset,
  status (active/paused/erroring), health counters.
- **Mapping**: declarative rules turning one incoming payload into one or more
  records of a dataset (paths, renames, conversions, constant dimensions,
  collection expansion, record identity for de-duplication).
- **Dataset**: a named collection of records with a declared timestamp,
  measures, dimensions and retention; may be fed by several sources.
- **Record**: one measurement — timestamp, dimension values (e.g. device,
  run, type), measure values.
- **Calculated measure**: named arithmetic expression over a dataset's
  measures.
- **Query**: dataset + measures + aggregation + filters + grouping + time
  range + bucket; the only way any widget reads data.
- **Widget**: query + visualization type + display options.
- **Dashboard**: grid of widgets, variables, default time range, refresh
  interval, owner, visibility.
- **User**: account, role, preferences, favourites, home dashboard.
- **Organization**: the single owner scope every definition and user belongs
  to; exactly one per installation in v1 (FR-020).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An admin connects a new push source and sees its data on a chart
  in under 10 minutes, with zero code changes, rebuilds or redeploys.
- **SC-002**: Temperature and epidemic views are reproduced 100% from
  definitions; a search of backend and frontend source finds zero
  temperature- or epidemic-specific types, screens or addresses.
- **SC-003**: Existing devices send through the cutover with zero firmware
  changes and zero lost records.
- **SC-004**: At home-lab scale (10 sources, 5 million records), a dashboard
  with 8 widgets over 30 days of data fully renders in under 2 seconds for 95%
  of loads.
- **SC-005**: Sustained 500 records/second ingestion across all sources with
  no loss and records queryable within 5 seconds.
- **SC-006**: A rejected payload is visible on the source status page with its
  reason within 1 minute.
- **SC-007**: Export → import onto an empty installation reproduces every
  source, dataset and dashboard such that the same data renders identical
  widgets.
- **SC-008**: Access tests across all three roles show zero cases of a user
  seeing or changing something their role or a dashboard's visibility forbids.

## Assumptions

- Self-hosted, single deployment at home-lab scale (see SC-004/SC-005); not a
  hosted multi-customer service (multiple organizations are a later step,
  FR-020).
- Backend and frontend stay separate deployables that talk only through the
  documented contracts (FR-014); the frontend keeps today's targets (web and
  desktop).
- Sign-in uses platform-managed accounts; single sign-on with an external
  identity provider is a later option, not v1.
- "Live" means periodic auto-refresh (seconds to hours); push-to-browser
  streaming, alerting/notifications and data export (CSV) are out of scope for
  v1.
- The Home Assistant integration remains a separate project; on this platform
  it is a collector app feeding a source (FR-024), or a pull source.
- FR-023 compatibility covers device ingestion only; the current epidemic
  run-details screen is already broken (see research.md §1) and is replaced,
  not fixed.
- Constitution v2.0.0 (2026-10-27) replaces the plugin-per-domain principles
  with configuration over code, generic versioned contracts and auth in
  scope; `user-roadmap.md` is superseded by this spec.
