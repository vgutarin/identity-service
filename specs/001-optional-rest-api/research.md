# Phase 0 Research: Optional REST API on Vaadin App Startup

## Context discovered in the existing codebase

- `IdentityApplicationApi` (in `identity-api`) is the shared contract. It has two implementations:
  - **Embedded**: `IdentityApplicationApiService implements IdentityApplicationApi` in `identity-logic`.
  - **Remote**: `IdentityApplicationApiRestClient extends IdentityApplicationApi` in `identity-rest-client`
    (declarative `@HttpExchange`, sends `X-VG-Identity-API-Key`).
- The HTTP server surface lives in `identity-rest-service`:
  - `IdentityApplicationController` (`/api/v1/applications/me`, `/me/authentications/telegram`),
    wired to `identity-logic` services.
  - `ApiKeyAuthenticationFilter` (validates the single `X-VG-Identity-API-Key` header via
    `IdentityApiKeyService`).
  - `RestSecurityConfiguration` — a `SecurityFilterChain` at `@Order(1)` with
    `securityMatcher("/api/**")`, stateless, `anyRequest().authenticated()`, 401 entry point.
  - `IdentityApplication` — the standalone Spring Boot main.
  - `IdentityUserController` is entirely commented out (out of scope).
- `identity-frontend-vaadin` depends on `identity-logic` (embedded), runs Vaadin Flow with its own
  `SecurityConfiguration` (`VaadinSecurityConfigurer`), and does **not** expose the REST controllers.
- `identity-logic` depends on `spring-web` but **not** `spring-boot-starter-web` (no Spring MVC).

## Decision 1 — Where the REST controllers live

**Decision**: Split the current `identity-rest-service` application module into two:
- **`identity-rest-server`** — a `java-library` holding the controllers, `ApiKeyAuthenticationFilter`,
  the `/api/**` security chain, and a new `@AutoConfiguration` that registers them conditionally. No
  `@SpringBootApplication`, no Spring Boot application plugin.
- **`identity-rest-app`** — a thin Spring Boot runner (the moved `IdentityApplication` main + its
  properties) that depends on `identity-rest-server` and defaults the toggle to `true`.

Both `identity-rest-app` and `identity-frontend-vaadin` depend on `identity-rest-server`. Do **not**
move controllers into `identity-logic`, and do **not** keep a single dual-purpose module.

**Rationale**:
- `identity-logic` deliberately carries no Spring MVC dependency. Moving `@RestController` classes
  there would drag `spring-boot-starter-web`/`spring-webmvc` into every consumer of the logic library,
  coupling business logic to the HTTP transport and violating the layered separation the modules
  already express.
- The controllers are the server side of the **remote** implementation of `IdentityApplicationApi`;
  their natural home is a web library, mirroring `identity-rest-client` (client) ↔
  `identity-rest-server` (server).
- A single dual-purpose `identity-rest-service` module is awkward and unsafe to consume as a library:
  (1) the `org.springframework.boot` plugin makes the primary artifact a repackaged fat `bootJar`, not
  a clean library jar; and (2) its `@SpringBootApplication` main lives in package `vg.identity`, which
  the Vaadin app's component scan (`scanBasePackages = {"vg.identity", "vg.unique.id"}`) would pick up
  as a second application/configuration and break the context. A pure library with no
  `@SpringBootApplication` eliminates both problems.
- Reuse is achieved by dependency + auto-config, idiomatic Spring Boot matching the existing
  `IdentityLogicAutoConfig` / `IdentityRestClientAutoConfig` pattern in the repo.

**Alternatives considered**:
- **Move controllers into `identity-logic` (original suggestion)**: simplest wiring, single module, but
  forces Spring MVC onto all logic consumers and mixes HTTP concerns into the domain layer. Rejected.
- **Keep one dual-purpose `identity-rest-service`** (library + thin main in the same module): smaller
  diff, but hits the fat-`bootJar`-as-dependency and duplicate-`@SpringBootApplication` scan problems
  above; would require re-enabling the plain `jar` and moving/excluding the main class anyway.
  Rejected in favor of the clean split.
- **Duplicate controllers in the Vaadin module**: violates DRY and the constitution's maintainability
  rule; two copies would drift. Rejected.
- **Naming `identity-rest-api`**: rejected to avoid confusion with the existing `identity-api` contract
  module; `identity-rest-server` pairs clearly with `identity-rest-client`.

## Decision 2 — How the mode is toggled

**Decision**: A single boolean property `identity.rest.api.enabled`, default `false`. All REST web
beans (controllers, `ApiKeyAuthenticationFilter`, the `/api/**` `SecurityFilterChain`) are registered
via an `@AutoConfiguration` class guarded by
`@ConditionalOnBooleanProperty("identity.rest.api.enabled")` (Spring Boot 4; equivalent to the older
`@ConditionalOnProperty(name = ..., havingValue = "true")`).

**Rationale**:
- One build, two runtime modes selected by configuration — satisfies FR-004.
- `@ConditionalOnBooleanProperty` defaults to matching only when the property is explicitly `true`;
  absence ⇒ disabled, giving the safe-by-default posture (FR-005, constitution Principle V). It is the
  purpose-built annotation for boolean feature flags in Boot 4 and reads more clearly than
  `@ConditionalOnProperty` with `havingValue`.
- When disabled the beans never register, so `/api/**` paths resolve to no endpoint (FR-002, SC-002),
  and the Vaadin security chain is untouched.
- The standalone `identity-rest-app` sets `identity.rest.api.enabled=true` in its own
  `application.properties`, preserving today's behavior.

**Alternatives considered**:
- **Spring profiles** (e.g. `rest-api` profile): works, but a property is more explicit and easier to
  set per environment/flag; profiles also risk carrying unrelated config. A profile can still be layered
  on top later. Rejected as the primary mechanism.
- **Separate deployables only** (keep two apps, no toggle): does not satisfy "start the Vaadin app with
  or without REST API". Rejected.

## Decision 3 — Security chain precedence in the Vaadin app

**Decision**: Register the `/api/**` API-key chain at `@Order(1)` (as it already is) so it precedes the
Vaadin `SecurityFilterChain`. The Vaadin chain keeps its current (default/lower) precedence and continues
to match everything else. Verify via test that `/api/**` in the Vaadin app requires the API key and is
never served under Vaadin's `permitAll`/anonymous rules.

**Rationale**:
- Spring Security evaluates multiple `SecurityFilterChain`s in `@Order`; the first whose
  `securityMatcher` matches wins. `securityMatcher("/api/**")` at `@Order(1)` guarantees API paths are
  handled by the API-key chain, satisfying "valid API key only" (FR-007, SC-004) and constitution
  Principle III.
- No change to Vaadin's CSRF/anonymous handling is needed because the API chain disables CSRF only for
  its own `/api/**` matcher.

**Alternatives considered**:
- **Single merged chain**: would entangle Vaadin session auth with stateless API-key auth and risk
  exposing `/api/**` under Vaadin rules. Rejected.

## Decision 4 — Equivalence between embedded and remote (constitution Principle VI/VII)

**Decision**: Add a shared conformance test that exercises the same operations through the embedded
`IdentityApplicationApiService` and through `IdentityApplicationApiRestClient` (against the app with the
REST API enabled) and asserts equivalent observable results. Reuse/extend
`identity-integration-tests` (currently depends on logic, rest-service, and rest-client; retarget the
rest-service dependency to `identity-rest-server`, and add `identity-rest-app` if a full standalone
boot is exercised).

**Rationale**: Directly enforces Principle VI (both implementations satisfy one contract, validated by a
shared suite) and FR-008/SC-003. The integration-tests module already wires all three participants.

**Alternatives considered**:
- **Test each implementation in isolation only**: would not catch behavioral drift between the two entry
  points. Rejected as insufficient for Principle VI.

## Open questions

None. All spec assumptions have a concrete grounding in the existing code; no `NEEDS CLARIFICATION`
remains.
