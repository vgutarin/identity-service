# Identity Application API (current state)

**Domain**: Application-facing identity API — the API-key-authenticated surface a registered application
uses to describe itself and to resolve users from Telegram sign-in data.

**Status**: Current. Reflects behavior as implemented (feature `003-telegram-principal-details`,
non-OIDC / typed-identifier revision of 2026-08-27).

## Surface

A single contract, `IdentityApplicationApi`, provided in two interchangeable implementations behind one
interface (constitution Principle VI):

- **Embedded** — in-process (`IdentityApplicationApiService`).
- **Remote** — REST over HTTP (`IdentityApplicationApiRestClient`), registered only when
  `identity.rest.api.enabled=true`.

Both are validated by a shared conformance suite asserting equivalent behavior for every operation.

Authentication: API key header `X-VG-Identity-API-Key`. Unauthenticated/unauthorized requests are
rejected before any resolution (default-deny).

## Identifiers and types

Principals are **not** OIDC-compatible. Identity fields use domain-native, self-explaining names and the
system's `UniqueId` type — never raw strings or numbers. `UniqueId` and `URI` travel on the wire as
strings (`UniqueId` carries `@JsonValue`/`@JsonCreator`, so no custom serializer is required).

## Operations

### `GET /api/v1/applications/me` → application principal

Returns safe metadata for the calling application.

| Field | Type | Notes |
|-------|------|-------|
| `applicationUniqueId` | `UniqueId` | Identifier of the calling application. |
| `workspaceUniqueId` | `UniqueId` | Identifier of the owning workspace. |
| `name` | string | Application display name. |
| `uri` | `URI` | Application location/reference (well-formed). |

No secret/internal fields (e.g. the application payload) are exposed.

### `POST /api/v1/applications/me/authentications/telegram` → consent-gated user principal

Verifies Telegram WebApp init data and resolves a **consent-gated** principal. Request body:
`{ "telegramInitData": <raw>, "consentToKeepPersonalData": <bool, default false> }`.

Responses: `200` with the principal (resolved, either state); `204` when the data is invalid,
unverifiable, or not resolvable (neutral failure — nothing is persisted, nothing about the failure is
revealed).

Principal:

| Field | Type | Notes |
|-------|------|-------|
| `userUniqueId` | `UniqueId` \| null | Stable user id when established; **null** pre-consent. Never reassigned. |
| `name` | string | Display name from the verified Telegram profile (first+last, else username). |
| `permissions` | set of string | Caller-application-scoped permissions; empty when no established user. |
| `consentGiven` | boolean | `true` ⇔ `userUniqueId != null`. |

A reserved additional-data placeholder (`preferredLocale`, domain-native name) is defined for future,
additive use and is never populated by this feature.

## Consent gate (data safety)

- **No consent** → nothing is persisted and `userUniqueId` is null (provisional principal usable only to
  greet/identify).
- **Consent given** → the user is created if needed and a stable `userUniqueId` is returned; re-submitting
  resolves the same user with no duplicate.
- Legacy Telegram users provisioned before this behavior have no recorded consent; they resolve as
  pre-consent until the first consented call backfills consent.

## Auditability

Consent-triggered user creation and successful authentication are recorded through the
`vg.identity.security.audit` logger, referencing the user by `userUniqueId` only. Audit entries never
contain the raw init data, Telegram profile fields, or credentials, and are sanitized against log
injection.

## Forward compatibility

New optional fields may be added to either principal without breaking existing consumers: the typed
client deserializes with unknown-property tolerance (Jackson default), so a consumer reading only the
currently-defined fields keeps working.

## Explicitly out of scope

OIDC compatibility (claim shape and protocol) and populating the `preferredLocale` placeholder.
