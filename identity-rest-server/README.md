# identity-rest-server

The server side of the identity REST API: controllers, API-key security, and the auto-configuration
that exposes them. It is a library — the standalone runner is `identity-rest-app`, and the Vaadin app
(`identity-frontend-vaadin`) can also host it.

## Enabling the REST API

The REST surface is **disabled by default** (safe-by-default). It is controlled by a single property:

| Property | Default | Effect |
|----------|---------|--------|
| `identity.rest.api.enabled` | `false` | `true` exposes `/api/**`; `false`/absent runs without it. |

- `identity-rest-app` sets it to `true` (the standalone REST deployment is always on).
- `identity-frontend-vaadin` sets it to `false`; enable it per environment without rebuilding, e.g.:

  ```sh
  IDENTITY_REST_API_ENABLED=true ./gradlew :identity-frontend-vaadin:bootRun
  # or
  ./gradlew :identity-frontend-vaadin:bootRun --args='--identity.rest.api.enabled=true'
  ```

An unparseable value (e.g. `maybe`) fails startup rather than silently disabling the API. The effective
mode is logged at startup (`REST API is ENABLED/DISABLED …`).

When enabled, the `/api/**` security chain runs at `@Order(1)`, ahead of the Vaadin UI chain, so API
paths are always matched by the API-key chain and never served under the UI's rules. The API-key filter
participates only in that chain — it is not registered as a global servlet filter, so the UI is
unaffected.

## Application API-key authentication

Machine clients authenticate with one `X-VG-Identity-API-Key` header. Keys are issued for an
`IdentityApplication` by an administrator in the Vaadin application-management screen. The full value is
displayed only once; store it in the client's secret manager and never in source code, URLs, or logs.

Every key has a required expiry and can be revoked immediately. Authentication is sessionless, but the
service checks the key on every request so revocation and expiry apply without delay. Use TLS for every
request.

The initial authenticated endpoint returns only the calling application's safe metadata:

```sh
curl --fail-with-body \
  -H "X-VG-Identity-API-Key: ${IDENTITY_API_KEY}" \
  https://identity.example.com/api/v1/applications/me
```

It returns `sub`, `workspaceUniqueId`, `name`, and `uri`. It never returns the API key, key hash, or
application payload. Missing, malformed, expired, revoked, or otherwise invalid keys receive
`401 Unauthorized` without credential-specific details.

## Telegram authentication → consent-gated principal

Applications resolve a user from Telegram Mini App sign-in data and receive an OIDC-claim-compatible
**principal**:

```sh
curl --fail-with-body \
  -H "X-VG-Identity-API-Key: ${IDENTITY_API_KEY}" \
  -H "Content-Type: application/json" \
  -d '{"telegramInitData":"<raw Telegram initData>","consentToKeepPersonalData":false}' \
  https://identity.example.com/api/v1/applications/me/authentications/telegram
```

The request body carries the raw Telegram `initData` (verified server-side; kept out of the URL) and a
`consentToKeepPersonalData` flag that defaults to `false` when omitted.

Consent gates whether any user data is stored:

| Case | Result |
|------|--------|
| Data invalid / unverifiable / not resolvable | `204 No Content` — nothing is persisted (does not reveal which input failed). |
| Valid, **consent not given**, user not yet established | `200` — principal with `sub: null`, empty `permissions`, `consentGiven: false`. **No user is created or stored.** |
| Valid, **consent given** | `200` — the user is created (or a pre-existing one reused), `sub` is a stable id, `permissions` are the caller-application's scoped set, `consentGiven: true`. |
| Already-established user | `200` — resolves to the same stable `sub` even if the request omits consent. |

Response body:

```json
{ "sub": "…stable-id or null…", "name": "Ada Lovelace", "permissions": ["orders.read"], "consentGiven": true }
```

- Identity fields use **OIDC standard claim names**: `sub` (subject) and `name`. `permissions` and
  `consentGiven` are additional, non-standard fields.
- A non-null `sub` always means consent was given; a null `sub` always means it was not (the two never
  disagree).
- The object is OIDC **claim-shape** compatible only. Additional optional claims such as `locale` are
  reserved for a future, additive change (adding one will not break existing consumers). Full OIDC
  protocol support (discovery, token, UserInfo endpoints, signed ID tokens) is out of scope.

> **⚠️ Breaking change (feature 003).** The earlier version of this operation accepted a `text/plain`
> `initData` body, **provisioned a user unconditionally (no consent)**, and returned
> `{ applicationUniqueId, identityUserUniqueId, claimsByScope }`. It now requires a **JSON** body with the
> consent flag, **creates no user until consent is given**, and returns the reshaped principal above
> (`sub` / `name` / `permissions` / `consentGiven`). Consumers must migrate:
> - send `Content-Type: application/json` with `{ "telegramInitData": …, "consentToKeepPersonalData": … }`;
> - read `sub` instead of `identityUserUniqueId`, and `permissions` instead of `claimsByScope[...]`;
> - treat a null `sub` (consent pending) as "not yet established" rather than an error.

## Security audit logging

When the REST API is enabled, authentication events are recorded through a dedicated logger,
`vg.identity.security.audit`:

- **Failures** are logged **per-event, immediately** — `reason` (`missing_header`, `multiple_headers`,
  `invalid_key`), method, path, `remoteAddr`, and, when a key value was supplied, the key **id** (a UUID —
  never the secret).
- **Successes** are **counted** per `(applicationUniqueId, method, path)` and flushed periodically as
  aggregates (count + window bounds), to avoid flooding the log on the high-volume success path.
- **Telegram authentication** additionally emits two per-event records on the same logger: a
  `telegram.user.provision` event when consent creates a new user, and a `telegram.auth` success event.
  Both reference the user by stable `sub` only — never the raw `initData`, Telegram profile fields, or the
  API key.

| Property | Default | Meaning |
|----------|---------|---------|
| `identity.rest.api.audit.flush-interval` | `PT10M` | How often success counters are flushed (ISO-8601 duration). |
| `identity.rest.api.audit.max-counter-keys` | `10000` | Cap on distinct success-counter keys held in memory; excess folds into an `__overflow__` bucket. |

The API key value is never logged. **Tamper-evidence is operational**: route the `vg.identity.security.audit`
logger to an append-only / WORM sink with appropriate retention (constitution Principle V).

## Verifying

```sh
./gradlew :identity-rest-server:test
# end-to-end (needs Docker for the MySQL test container):
./gradlew :identity-integration-tests:test
```
