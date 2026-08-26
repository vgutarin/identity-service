# Contract: Telegram Authentication → Consent-Gated Principal

**Feature**: 003-telegram-principal-details | **Date**: 2026-08-23

Transport: HTTP (Spring MVC controller in `identity-rest-server`) + typed Java client
(`identity-rest-client`, `IdentityApplicationApiRestClient`). Both implement the `identity-api`
interface `IdentityApplicationApi`. Registered only when `identity.rest.api.enabled=true`.

## Operation

`POST /api/v1/applications/me/authentications/telegram`

**Authentication**: API key header `X-VG-Identity-API-Key` (existing `ApiKeyAuthenticationFilter`).
Unauthenticated/unauthorized requests are rejected before any resolution (default-deny).

**Breaking change**: this operation previously provisioned a user unconditionally and returned a
claims-scoped principal. It now requires consent to create/persist a user and returns the domain-native
principal below (identifiers typed as `UniqueId`, **not** OIDC claims). Callers of the old contract
must migrate.

### Request

Content-Type: `application/json`

```json
{
  "telegramInitData": "<raw Telegram WebApp initData>",
  "consentToKeepPersonalData": false
}
```

- `telegramInitData` (string, required) — untrusted until verified; carried in the body, never the URL.
- `consentToKeepPersonalData` (boolean, optional, default `false`) — safe default is no consent.

### Responses

| Status | When | Body |
|--------|------|------|
| `200 OK` | Telegram data verified and resolved (either state) | Principal object (below) |
| `204 No Content` | Data invalid, unverifiable, or not resolvable | *(empty; reveals nothing about which input failed)* |
| `401 / 403` | Missing/invalid API key | *(error; no resolution performed)* |

### Principal object (200 body)

```json
{
  "userUniqueId": "3kf9Qz7Xa2",
  "name": "Ada Lovelace",
  "permissions": ["orders.read", "orders.write"],
  "consentGiven": true
}
```

(`userUniqueId` is the `UniqueId` **encoded string** form, not the raw numeric value.)

Pre-consent example (no consent granted, nothing stored):

```json
{
  "userUniqueId": null,
  "name": "Ada Lovelace",
  "permissions": [],
  "consentGiven": false
}
```

Field rules (see [data-model.md](../data-model.md) for full invariants):
- `userUniqueId` — the system's `UniqueId` (carried as its canonical string form); stable user id when
  `consentGiven=true`; **null** when `consentGiven=false`. Not an OIDC claim.
- `name` — display name from the verified Telegram profile; present in both states.
- `permissions` — caller-application-scoped set; empty when `userUniqueId` is null.
- `consentGiven` — boolean; consistent with `userUniqueId` nullability (INV-1).
- `preferredLocale` — reserved domain-native placeholder; **omitted** by this feature (unpopulated).

### Behavioral guarantees (map to FRs)

- No user or personal data is created/persisted when `consentToKeepPersonalData` is false/absent and
  no established user exists (FR-003, FR-004).
- `consentToKeepPersonalData=true` for a not-yet-established user creates exactly one user and returns
  a non-null `userUniqueId`; re-submitting resolves the same `userUniqueId` with no duplicate (FR-005, FR-006).
- Invalid/unverifiable data → 204, nothing persisted, regardless of consent (FR-008, FR-013).
- Consent-triggered creation and authentication are audited without secrets/PII (FR-022, FR-023).
- Identifiers are typed as `UniqueId` with self-explaining names; the principal is **not**
  OIDC-compatible (FR-017, FR-018).

## Companion operation — application self-metadata

`GET /api/v1/applications/me`

Returns the **application principal** describing the calling application. Retyped in this change to use
`UniqueId`/`URI` and self-explaining names; not OIDC-compatible.

```json
{
  "applicationUniqueId": "9bQ2Ltp",
  "workspaceUniqueId": "Xa71mK",
  "name": "Orders Bot",
  "uri": "https://t.me/orders_bot"
}
```

- `applicationUniqueId` — `UniqueId` (encoded string form); was `sub: String`.
- `workspaceUniqueId` — `UniqueId` (encoded string form); was `Long`.
- `name` — application display name.
- `uri` — `URI` (string form), well-formed; was `String`.

## Client interface (identity-rest-client)

`IdentityApplicationApiRestClient` mirrors the contract. The `IdentityApplicationApi.authenticateTelegram`
signature changes to accept the consent flag and return the reshaped principal (e.g.
`Optional<IdentityApplicationUserPrincipal> authenticateTelegram(String telegramInitData, boolean consentToKeepPersonalData)`),
with `Optional.empty()` mapping to HTTP 204.
