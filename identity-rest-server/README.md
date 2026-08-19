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

It returns `uniqueId`, `workspaceUniqueId`, `name`, and `uri`. It never returns the API key, key hash, or
application payload. Missing, malformed, expired, revoked, or otherwise invalid keys receive
`401 Unauthorized` without credential-specific details.

## Security audit logging

When the REST API is enabled, authentication events are recorded through a dedicated logger,
`vg.identity.security.audit`:

- **Failures** are logged **per-event, immediately** — `reason` (`missing_header`, `multiple_headers`,
  `invalid_key`), method, path, `remoteAddr`, and, when a key value was supplied, the key **id** (a UUID —
  never the secret).
- **Successes** are **counted** per `(applicationUniqueId, method, path)` and flushed periodically as
  aggregates (count + window bounds), to avoid flooding the log on the high-volume success path.

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
