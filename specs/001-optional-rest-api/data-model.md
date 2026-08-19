# Phase 1 Data Model: Optional REST API on Vaadin App Startup

This feature is a runtime-composition/configuration feature. It introduces **no persistent entities**
and **no database schema change**. The only new "entity" is a configuration/runtime concept.

## Configuration Entity

### RestApiToggle (configuration)

Represents the operator's choice of whether the REST API surface is exposed by the running application.

| Field | Type | Rules | Notes |
|-------|------|-------|-------|
| `identity.rest.api.enabled` | boolean | Optional; default `false` when absent | Safe-by-default (FR-005). `true` ⇒ REST controllers + `/api/**` API-key chain registered; `false`/absent ⇒ not registered. |

**Validation / failure behavior**:
- A recognizable boolean (`true`/`false`, case-insensitive) is accepted.
- A value that cannot be bound to a boolean MUST cause startup to fail fast with a clear error
  (FR-009, SC-006) — this is the standard Spring relaxed-binding behavior for a typed `boolean`
  property; the plan relies on it rather than adding custom parsing.

**Lifecycle / state**:
- Resolved once at application startup and fixed for the process lifetime (spec assumption: no runtime
  toggling).
- Effective value is logged at startup and surfaced via health/status information (FR-006).

**Derived runtime state — "Application Runtime Mode"** (the spec's Key Entity):

| Mode | Condition | Observable effect |
|------|-----------|-------------------|
| REST API disabled | `identity.rest.api.enabled` absent or `false` | No `/api/**` endpoints; Vaadin UI fully functional. |
| REST API enabled | `identity.rest.api.enabled=true` | `/api/**` endpoints exposed, each requiring a valid API key; Vaadin UI fully functional. |

## Existing entities referenced (unchanged)

These already exist and are **not** modified by this feature; listed for context only.

- **IdentityApplicationApi** (`identity-api`) — the shared contract with `me()` and
  `authenticateTelegram(initData)`.
- **AuthenticatedIdentityApplication**, **IdentityApplicationUserPrincipal**,
  **IdentityApplicationPrincipal** (`identity-api` model) — request/response payloads for the contract.
- **API Key** — validated by `IdentityApiKeyService`; supplied via the `X-VG-Identity-API-Key` header.
  Treated as a secret credential; never logged. No schema change.
