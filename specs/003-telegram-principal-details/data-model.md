# Data Model: Consent-Gated Principal From Telegram Data

**Feature**: 003-telegram-principal-details | **Date**: 2026-08-23

This feature adds **no** new persisted tables. It reshapes one response object and changes when an
existing entity field is written.

## Response object — Principal (OIDC-compatible)

Reshaped from the existing `IdentityApplicationUserPrincipal` record (`identity-api`,
`vg.identity.model`). Serialized to JSON; optional/absent fields are omitted (not null placeholders),
except `sub` which is explicitly present-and-null in the pre-consent state to signal "not established".

| Field | OIDC claim | Type | Required | Notes |
|-------|-----------|------|----------|-------|
| `sub` | `sub` | string \| null | always present | Stable user unique id when established; **null** pre-consent. Never reassigned/recycled. |
| `name` | `name` | string | yes | Display name derived from the verified Telegram profile (first+last, else username). Present in both states. |
| `permissions` | *(non-standard)* | set of string | yes | Caller-application-scoped permission strings; **empty** when no established user. Never collides with an OIDC claim name. |
| `consentGiven` | *(non-standard)* | boolean | yes | `true` ⇒ `sub` non-null; `false` ⇒ `sub` null (FR-007). |
| `locale` | `locale` | string | no | **Reserved placeholder**, always omitted by this feature. When later populated: BCP-47 language tag. |

**Validation / invariants**:
- INV-1: `consentGiven == false` ⇔ `sub == null`; `consentGiven == true` ⇔ `sub != null` (FR-007).
- INV-2: `permissions` is empty whenever `sub == null` (FR-009).
- INV-3: `name` is non-blank whenever any Telegram identifying value exists (fallback to username, FR-008).
- INV-4: `locale` is absent in every response produced by this feature (FR-010).
- INV-5: no secrets/tokens/raw Telegram data appear in any field (FR-014).

**Shared constant**: `PERMISSIONS_SCOPE` (currently on this record, used by Vaadin admin + claim
tests) is retained or relocated to a neutral home; `permissions` is derived from
`claimsByScope.get(PERMISSIONS_SCOPE)` flattened.

## Request object — Telegram authentication request

Replaces the current `text/plain` init-data body.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `telegramInitData` | string | yes | Raw Telegram WebApp init data; untrusted until verified; carried in the body, never the URL. |
| `consentToKeepPersonalData` | boolean | no (default `false`) | Whether the user granted consent this request. Safe default = no consent. |

## Existing entity — IdentityUserEntity (write-timing change only)

`identity-logic`, `vg.identity.entity`. No schema change.

| Field | Change |
|-------|--------|
| `consentToKeepPersonalDataAt` (Instant) | Now set **at the moment consent is granted** on the Telegram API path (previously never set on this path). Presence = consent recorded; drives `sub` exposure and `consentGiven`. |

**Lifecycle (Telegram API path)**:

```text
verify init data
   ├─ invalid            → (no entity)            → 204, no principal
   └─ valid → lookup by Telegram id
        ├─ not found + no consent  → (no entity)        → pre-consent principal (sub=null)
        ├─ not found + consent     → CREATE user + bind + set consentAt → established principal (sub set)  [AUDIT: provision + auth]
        ├─ found, consentAt set    → (no write)         → established principal (sub set)                  [AUDIT: auth]
        ├─ found, consentAt null + consent → set consentAt → established principal (sub set)               [AUDIT: consent-backfill + auth]
        └─ found, consentAt null + no consent → (no write) → pre-consent principal (sub=null)
```

## Audit records (log-only, not persisted rows)

Emitted via the `vg.identity.security.audit` logger (structured, sanitized, no secrets/PII):

| Event | When | Fields (illustrative) |
|-------|------|-----------------------|
| `telegram.user.provision` / `outcome=CREATED` | consent-triggered user creation | `sub`, `applicationUniqueId`, timestamp |
| `telegram.auth` / `outcome=SUCCESS` | authentication resolves an established user | `sub`, `applicationUniqueId`, timestamp |

Audit entries reference the user by `sub` only; they never include `telegramInitData`, Telegram
profile fields, or the API key.
