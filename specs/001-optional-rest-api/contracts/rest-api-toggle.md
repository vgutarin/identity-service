# Contract: REST API Startup Toggle

Defines the operator-facing configuration contract for enabling/disabling the REST API in any host
application (the Vaadin app and the standalone REST app).

## Property

| Property | Type | Default | Meaning |
|----------|------|---------|---------|
| `identity.rest.api.enabled` | boolean | `false` | `true` exposes the REST API; `false`/absent runs without it. |

## Guarantees

- **G1 (safe default)**: If the property is absent, the application starts with the REST API disabled.
- **G2 (disabled ⇒ no endpoints)**: When disabled, requests to any `/api/**` path are not served by a
  REST endpoint (no controller and no `/api/**` security chain are registered).
- **G3 (enabled ⇒ endpoints + auth)**: When enabled, `/api/**` endpoints are registered and every
  request to them is authenticated by API key (see [rest-api-key-auth.md](rest-api-key-auth.md)).
- **G4 (UI unaffected)**: The value never changes UI availability or behavior in the Vaadin app.
- **G5 (fail fast)**: A value that cannot bind to a boolean fails application startup with a clear error;
  the app never starts in an ambiguous mode.
- **G6 (observable)**: The effective mode is logged at startup and reflected in health/status output.
- **G7 (immutable per run)**: The mode is fixed for the process lifetime; changing it requires restart.

## Host defaults

| Host application | Default in its `application.properties` |
|------------------|------------------------------------------|
| `identity-frontend-vaadin` | `identity.rest.api.enabled=false` (explicit, matches G1) |
| `identity-rest-app` (standalone runner) | `identity.rest.api.enabled=true` (preserves current behavior) |

## Acceptance mapping

- FR-001, FR-004 → G1, G3, G7
- FR-002, FR-010 → G2, G4
- FR-005 → G1
- FR-006 → G6
- FR-009 → G5
