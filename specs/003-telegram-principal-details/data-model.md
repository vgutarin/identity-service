# Data Model: Consent-Gated Principal From Telegram Data

**Feature**: 003-telegram-principal-details | **Date**: 2026-08-27

This feature adds **no** new persisted tables. It reshapes/retypes two response objects and changes
when an existing entity field is written. The principals are **not** OIDC-compatible (decided
2026-08-27); identity fields use domain-native, self-explaining names and the system's
unique-identifier type `UniqueId` (`vg.unique.id.model.UniqueId`).

**`UniqueId` representation**: `UniqueId` is `Serializable` and wraps a `long`. On the wire it is a
JSON **string** with **no custom serializer required** — it carries `@JsonValue` on `toString()` (which
returns the encoded string form) and `@JsonCreator(mode = DELEGATING)` on `parse(String)`, so Jackson
serializes/deserializes it as a string on both server and client automatically. The JSON value is the
encoded token (not the raw `long`). `URI` is carried as its string form (Jackson built-in).

## Response object — Telegram user principal

Reshaped from the existing `IdentityApplicationUserPrincipal` record (`identity-api`,
`vg.identity.model`). Serialized to JSON; optional/absent fields are omitted (not null placeholders),
except `userUniqueId` which is explicitly present-and-null in the pre-consent state to signal "not
established".

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `userUniqueId` | `UniqueId` \| null | always present | Stable user unique id when established; **null** pre-consent. Never reassigned/recycled. Was `sub: String`. |
| `name` | string | yes | Display name derived from the verified Telegram profile (first+last, else username). Present in both states. |
| `permissions` | set of string | yes | Caller-application-scoped permission strings; **empty** when no established user. |
| `consentGiven` | boolean | yes | `true` ⇒ `userUniqueId` non-null; `false` ⇒ `userUniqueId` null (FR-007). |
| `preferredLocale` | locale (BCP-47) | no | **Reserved placeholder** (domain-native name), always omitted by this feature. When later populated: a locale-aware value. |

**Validation / invariants**:
- INV-1: `consentGiven == false` ⇔ `userUniqueId == null`; `consentGiven == true` ⇔ `userUniqueId != null` (FR-007).
- INV-2: `permissions` is empty whenever `userUniqueId == null` (FR-009).
- INV-3: `name` is non-blank whenever any Telegram identifying value exists (fallback to username, FR-008).
- INV-4: `preferredLocale` is absent in every response produced by this feature (FR-010).
- INV-5: no secrets/tokens/raw Telegram data appear in any field (FR-014).
- INV-6: `userUniqueId` is a `UniqueId` (never a raw string/number) and, when present, is stable (FR-018, FR-020).

**Shared constant**: `PERMISSIONS_SCOPE` (currently on this record, used by Vaadin admin + claim
tests) is retained or relocated to a neutral home; `permissions` is derived from
`claimsByScope.get(PERMISSIONS_SCOPE)` flattened.

## Response object — Application principal (`me()`)

The `IdentityApplicationPrincipal` record (`identity-api`, `vg.identity.model`) returned by
`GET /api/v1/applications/me`. Retyped to use `UniqueId`/`URI` and self-explaining names; not
OIDC-compatible.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `applicationUniqueId` | `UniqueId` | yes | Identifier of the calling application. Was `sub: String` (built via `getUniqueId().toString()`). |
| `workspaceUniqueId` | `UniqueId` | yes | Identifier of the owning workspace. Was `Long`. |
| `name` | string | yes | Application display name. |
| `uri` | `URI` | yes | Application location/reference. Was `String`. |

**Mapping note**: in `IdentityApplicationApiService.me()`, `application.getUniqueId()` is already a
`UniqueId` (pass through, drop `.toString()`); `getWorkspaceUniqueId()` returns `Long` (wrap as
`new UniqueId(...)`); `getUri()` returns `String` (parse to `URI`). Validation of `uri` well-formedness
happens at construction (URI parsing).

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
| `consentToKeepPersonalDataAt` (Instant) | Now set **at the moment consent is granted** on the Telegram API path (previously never set on this path). Presence = consent recorded; drives `userUniqueId` exposure and `consentGiven`. |

**Lifecycle (Telegram API path)**:

```text
verify init data
   ├─ invalid            → (no entity)            → 204, no principal
   └─ valid → lookup by Telegram id
        ├─ not found + no consent  → (no entity)        → pre-consent principal (userUniqueId=null)
        ├─ not found + consent     → CREATE user + bind + set consentAt → established principal (userUniqueId set)  [AUDIT: provision + auth]
        ├─ found, consentAt set    → (no write)         → established principal (userUniqueId set)                  [AUDIT: auth]
        ├─ found, consentAt null + consent → set consentAt → established principal (userUniqueId set)               [AUDIT: consent-backfill + auth]
        └─ found, consentAt null + no consent → (no write) → pre-consent principal (userUniqueId=null)
```

## Audit records (log-only, not persisted rows)

Emitted via the `vg.identity.security.audit` logger (structured, sanitized, no secrets/PII):

| Event | When | Fields (illustrative) |
|-------|------|-----------------------|
| `telegram.user.provision` / `outcome=CREATED` | consent-triggered user creation | `userUniqueId`, `applicationUniqueId`, timestamp |
| `telegram.auth` / `outcome=SUCCESS` | authentication resolves an established user | `userUniqueId`, `applicationUniqueId`, timestamp |

Audit entries reference the user by `userUniqueId` only; they never include `telegramInitData`,
Telegram profile fields, or the API key.
