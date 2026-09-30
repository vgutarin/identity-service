# Identity Application API (current state)

**Domain**: Application-facing identity API — the API-key-authenticated surface a registered application
uses to describe itself and to resolve users from Telegram sign-in data.

**Status**: Current.

## Surface

A single contract, `IdentityApplicationApi`, has an embedded service implementation and a typed HTTP
client:

- **Embedded** — in-process (`IdentityApplicationApiService`).
- **Remote** — REST over HTTP (`IdentityApplicationApiRestClient`). The server exposes the HTTP routes only
  when `identity.rest.api.enabled=true`.

The embedded implementation and the REST controller are covered by a shared conformance suite.

Authentication: API key header `X-VG-Identity-API-Key`. Unauthenticated or unauthorized requests are
rejected before any resolution.

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

### Telegram-shared phone contact

An application can ask its Mini App user to invoke Telegram's `requestContact()` interaction. Telegram sends
the contact to the application's bot; the Mini App retries the API call below after Telegram reports that the
contact was sent.

Identity receives contacts at `POST /api/v1/telegram/webhooks/{applicationUniqueId}`. This public webhook
authenticates Telegram's secret header against the application's currently stored bot ID, not the route
identifier alone. The webhook does not require an application API key; this exemption matches the path
relative to the servlet context, including deployments under a prefix such as `/identity`. Other API routes
remain API-key-protected. See [webhook operation and configuration](../engineering/engineering-notes.md#telegram-bot-webhooks).
Bot saves schedule setup asynchronously; phone sharing requires successful webhook setup before delivery.

| Method | Route | Result |
| --- | --- | --- |
| `GET` | `/api/v1/applications/me/users/{userUniqueId}/telegram-phone` | `200` with `{ "phoneNumber": "+<E.164>" }` only when the user shared a self-contact with this application's bot; otherwise `204`. |
| `DELETE` | `/api/v1/applications/me/users/{userUniqueId}/telegram-phone` | `204`; revokes only the calling application's ability to read the number. |

The user id is the established `userUniqueId` returned by Telegram authentication. A phone is global to the
user's encrypted Telegram-channel payload, while disclosure is application-scoped: ordinal `0` in the compact
`f` flag set in that application's existing application-user metadata controls Telegram-phone disclosure. A
second application has no access until the user shares their contact with that application's bot. A later
successful share replaces the stored number and is visible to applications whose membership flag is set.

The value is canonical E.164 contact data voluntarily shared through Telegram. It is not SMS/voice proof of
phone control and is not usable for phone login, recovery, or high-assurance verification.

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

Telegram phone webhook update payloads and phone values are likewise excluded from application and security logs.

## Forward compatibility

New optional fields may be added to either principal without breaking existing consumers: the typed
client deserializes with unknown-property tolerance (Jackson default), so a consumer reading only the
currently-defined fields keeps working.

## Explicitly out of scope

OIDC compatibility (claim shape and protocol) and populating the `preferredLocale` placeholder.
