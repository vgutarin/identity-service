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
claims-scoped principal. It now requires consent to create/persist a user and returns the
OIDC-compatible principal below. Callers of the old contract must migrate.

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
  "sub": "b1b2...-stable-user-id",
  "name": "Ada Lovelace",
  "permissions": ["orders.read", "orders.write"],
  "consentGiven": true
}
```

Pre-consent example (no consent granted, nothing stored):

```json
{
  "sub": null,
  "name": "Ada Lovelace",
  "permissions": [],
  "consentGiven": false
}
```

Field rules (see [data-model.md](../data-model.md) for full invariants):
- `sub` — stable user id when `consentGiven=true`; **null** when `consentGiven=false`. OIDC `sub`.
- `name` — display name from the verified Telegram profile; OIDC `name`; present in both states.
- `permissions` — caller-application-scoped set; empty when `sub` is null.
- `consentGiven` — boolean; consistent with `sub` nullability (INV-1).
- `locale` — reserved OIDC `locale` placeholder; **omitted** by this feature (unpopulated).

### Behavioral guarantees (map to FRs)

- No user or personal data is created/persisted when `consentToKeepPersonalData` is false/absent and
  no established user exists (FR-003, FR-004).
- `consentToKeepPersonalData=true` for a not-yet-established user creates exactly one user and returns
  a non-null `sub`; re-submitting resolves the same `sub` with no duplicate (FR-005, FR-006).
- Invalid/unverifiable data → 204, nothing persisted, regardless of consent (FR-008, FR-013).
- Consent-triggered creation and authentication are audited without secrets/PII (FR-022, FR-023).
- The object is OIDC-claim-compatible; full OIDC protocol (tokens/endpoints) is out of scope.

## Client interface (identity-rest-client)

`IdentityApplicationApiRestClient` mirrors the contract. The `IdentityApplicationApi.authenticateTelegram`
signature changes to accept the consent flag and return the reshaped principal (e.g.
`Optional<IdentityApplicationUserPrincipal> authenticateTelegram(String telegramInitData, boolean consentToKeepPersonalData)`),
with `Optional.empty()` mapping to HTTP 204.
