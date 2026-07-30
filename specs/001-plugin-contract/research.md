# Phase 0 Research: Plugin Contract (Phase 1)

No `NEEDS CLARIFICATION` markers in Technical Context — stack, storage, and test
framework are all fixed by the existing codebase. Decisions below are the concrete
choices made while filling the plan, not open unknowns.

## Decision: expose-only vs collect+expose interface shape

**Decision**: single `StatPlugin` interface with `id: String`, `expose(): PluginRouteSet`,
and `collect(raw: JsonElement)` — but `collect` is only *called* for push-based
plugins; expose-only plugins (Home Assistant, Phase 3) implement it as a no-op or
it's split into a smaller `ExposeOnlyPlugin` marker if that no-op reads awkwardly
once Phase 3 lands.

**Rationale**: `user-roadmap.md` explicitly asks for splitting collect/expose "into
two smaller interfaces if a plugin is expose-only" — but Phase 1 has zero expose-only
implementations yet (Home Assistant is Phase 3). Building the split now is
speculative; one interface with an optional-in-practice `collect` is the smaller
diff and doesn't block Phase 3 from introducing the split when there's a real
second shape to design against.

**Alternatives considered**: two interfaces (`CollectPlugin`, `ExposePlugin`) now —
rejected as premature (YAGNI, constitution Principle IV): no plugin needs it yet,
and guessing the right split before Phase 3's real pull-based use case would risk
designing the wrong contract.

## Decision: unknown-plugin-id error type

**Decision**: reuse the existing `EmptyDataException` (`exception/Exceptions.kt`),
already mapped to HTTP 404 in `GlobalExceptionHandler.kt`.

**Rationale**: roadmap F1.2 acceptance criterion is "throws a clear 404-mappable
error for unknown id" — `EmptyDataException` already does exactly that, registered
in `StatusPages`. Adding a new `UnknownPluginException` type would duplicate an
existing 404 mapping for no behavioral difference (ponytail rung 2: reuse what's
already in the codebase).

**Alternatives considered**: new dedicated exception type — rejected, no
information the caller needs that `EmptyDataException`'s message can't carry.

## Decision: where the generic key builder lives

**Decision**: add `getKey(pluginId, deviceId, runId?)` to the existing
`ApplicationConstants` object, alongside (not replacing yet) `getEpidemicKey`/
`getTemperatureKey`.

**Rationale**: FR-007 requires byte-identical output to the current per-domain
builders; keeping old and new side by side in the same file makes that diff/parity
trivial to review and test. FR-008 (no behavior change this phase) means the old
methods must stay callable until Phase 2 migrates their callers.

**Alternatives considered**: new `plugin/PluginKeyBuilder.kt` file — rejected as an
unnecessary extra file for one function; `ApplicationConstants` is already the
project's single key-building home.
