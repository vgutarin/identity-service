# Quickstart & Validation: Optional REST API on Vaadin App Startup

A run/validation guide proving the feature works end-to-end. Implementation details (bean bodies, test
source) belong in `tasks.md` / implementation, not here.

## Prerequisites

- JDK and Gradle wrapper as used by the repo (`./gradlew`).
- Docker/Colima running for integration tests that use Testcontainers (see the `colima-setup` skill).
- A valid API key value for manual REST checks (issued via `IdentityApiKeyService`; do not hard-code or
  log it).

## Build

```bash
./gradlew build
```

## Scenario 1 — Vaadin app WITHOUT the REST API (default, US1)

Start the Vaadin app with no REST-API override (safe default):

```bash
./gradlew :identity-frontend-vaadin:bootRun
```

Expected:
- Startup log states the REST API is **disabled**.
- The Vaadin UI loads and all UI operations work.
- A request to a REST path returns no REST endpoint (e.g. 404/handled by Vaadin, not by an API
  controller):

```bash
curl -i http://localhost:8080/api/v1/applications/me
```

Validates: FR-002, FR-005, FR-006, FR-010, SC-002, SC-005.

## Scenario 2 — Vaadin app WITH the REST API (US2)

Start the same build with the toggle enabled (property or env var):

```bash
./gradlew :identity-frontend-vaadin:bootRun --args='--identity.rest.api.enabled=true'
```

Expected:
- Startup log states the REST API is **enabled**.
- The Vaadin UI still loads and works.
- `/api/**` requires a valid API key:

```bash
# Missing/invalid key -> 401
curl -i http://localhost:8080/api/v1/applications/me
# Valid key -> 200 with application metadata
curl -i -H "X-VG-Identity-API-Key: <VALID_KEY>" http://localhost:8080/api/v1/applications/me
```

Validates: FR-001, FR-003, FR-006, FR-007, SC-003, SC-004.

## Scenario 3 — Same build, both modes (US3)

Run Scenario 1 and Scenario 2 against the **same** built artifact, changing only configuration. Confirm
UI works in both and the REST API is off then on.

Validates: FR-004, SC-001.

## Scenario 4 — Invalid toggle value fails fast

```bash
./gradlew :identity-frontend-vaadin:bootRun --args='--identity.rest.api.enabled=maybe'
```

Expected: startup fails with a clear binding error; the app does not start in an ambiguous state.

Validates: FR-009, SC-006.

## Scenario 5 — Standalone REST app unchanged

```bash
./gradlew :identity-rest-app:bootRun
```

Expected: REST API enabled by default (its own `application.properties` sets
`identity.rest.api.enabled=true`); behavior identical to today's `identity-rest-service`.

## Automated validation

- **Unit**: conditional wiring — controllers/filter/`/api/**` chain present iff
  `identity.rest.api.enabled=true`.
- **Functional/integration** (`identity-integration-tests`): Vaadin app context in both modes
  (endpoints absent when off; 401 without key and 200 with key when on); `/api/**` precedence over the
  Vaadin chain.
- **Conformance (Principle VI)**: same operations via embedded `IdentityApplicationApiService` and via
  `IdentityApplicationApiRestClient` return equivalent results.

Reference: [contracts/rest-api-toggle.md](contracts/rest-api-toggle.md),
[contracts/rest-api-key-auth.md](contracts/rest-api-key-auth.md), [data-model.md](data-model.md).
