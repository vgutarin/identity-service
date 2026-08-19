# Implementation Plan: Optional REST API on Vaadin App Startup

**Branch**: `001-optional-rest-api` | **Date**: 2026-08-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-optional-rest-api/spec.md`

## Summary

Allow the Vaadin application (`identity-frontend-vaadin`) to start in two modes selected purely by
startup configuration: with the REST API exposed, or without it (UI-only). The REST API surface —
controllers plus the API-key security chain — is extracted from the existing `identity-rest-service`
app module into a new **auto-configured web library `identity-rest-server`**, and the runnable
standalone app is reduced to a thin **`identity-rest-app`** module. Both `identity-rest-app` and
`identity-frontend-vaadin` depend on `identity-rest-server`. A single property
`identity.rest.api.enabled` (default `false`, safe-by-default) gates registration of the controllers
and the `/api/**` API-key-authenticated security filter chain. When disabled, no `/api/**` endpoints
exist; when enabled, every `/api/**` request requires a valid API key, exactly as it does in the
standalone REST app today.

This reuses the already-shared `IdentityApplicationApi` contract (embedded impl in `identity-logic`,
remote client in `identity-rest-client`) and keeps the HTTP layer out of the pure logic module.

## Technical Context

**Language/Version**: Java (Spring Boot 4.1.0, Spring Security 7, Jakarta EE), matching existing modules

**Primary Dependencies**: Spring Boot 4.1.0 (web MVC, security, data-jpa), Spring Security 7 filter
chains, Vaadin Flow 25.2.2 (`vaadin-spring-boot-starter`), Spring Boot auto-configuration via
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (mechanism already
used by `identity-logic`), and `@ConditionalOnBooleanProperty` (Boot 4) for the toggle

**Storage**: Existing relational store via JPA/Liquibase; no schema change for this feature

**Testing**: JUnit 5 (`useJUnitPlatform`), Spring Boot Test, `spring-security-test`, AssertJ;
existing `identity-integration-tests` module (Testcontainers/Docker for integration)

**Target Platform**: JVM server application (single deployable Vaadin app; separate standalone REST
app remains available)

**Project Type**: Multi-module web service + Vaadin web UI (Gradle multi-project)

**Performance Goals**: No new performance target; disabling the API must not add startup cost or
runtime overhead beyond a single conditional bean-registration check

**Constraints**: When the API is disabled, zero `/api/**` endpoints reachable; when enabled, the
`/api/**` chain MUST take precedence over the Vaadin security chain so API paths are never served
under Vaadin's anonymous/public rules; API keys are credentials and MUST NOT be logged

**Scale/Scope**: Scoped to the existing `IdentityApplicationController` endpoints
(`GET /api/v1/applications/me`, `POST /api/v1/applications/me/authentications/telegram`). The
commented-out `IdentityUserController` is out of scope. No new endpoints are added by this feature.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| I. Data Safety First | PASS — default posture reduces external surface (API off by default); no data path added. |
| II. Encryption Everywhere | PASS (deployment note) — no new at-rest data; API traffic MUST run over TLS in deployed environments (unchanged from today's REST app). |
| III. Authentication & Authorization Rigor | PASS — `/api/**` chain enforces API-key auth, `anyRequest().authenticated()`, default-deny with 401; design requires the `/api/**` chain to be ordered before the Vaadin chain so it cannot be bypassed. Spring Security 7 (Boot 4) keeps the multi-`SecurityFilterChain` + `securityMatcher` + `@Order` model used today. |
| IV. Modern Defense-in-Depth | PASS — stateless API-key auth, least privilege via existing principal authorities, no deprecated crypto introduced. |
| V. Secure by Default & Auditable | PASS — `identity.rest.api.enabled` defaults to `false`; effective mode is logged at startup and auth failures are observable; API keys never logged. |
| VI. Dual API Implementations | PASS — embedded (`IdentityApplicationApiService`) and remote (`IdentityApplicationApiRestClient`) already exist behind `IdentityApplicationApi`; this feature preserves both and adds the enable/disable toggle for the remote surface. |
| VII. Code Quality & Test Discipline | PASS (with required work) — feature adds unit tests (conditional wiring), functional/integration tests for both Vaadin modes, and a shared conformance check that embedded and remote produce equivalent results. |

**Result: PASS — no violations. Complexity Tracking section left empty.**

## Project Structure

### Documentation (this feature)

```text
specs/001-optional-rest-api/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── rest-api-key-auth.md
│   └── rest-api-toggle.md
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit-specify)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
identity-api/                         # Shared contract: IdentityApplicationApi, model DTOs (unchanged)
identity-logic/                       # Embedded impl: IdentityApplicationApiService (unchanged)
identity-rest-client/                 # Remote impl (client side): IdentityApplicationApiRestClient (unchanged)

identity-rest-server/                 # NEW java-library: REST server surface, auto-configured & conditional
├── build.gradle                      # java-library + spring-boot dependency-management (NO org.springframework.boot app plugin)
├── src/main/java/vg/identity/
│   ├── controller/
│   │   └── IdentityApplicationController.java        # moved from identity-rest-service; behavior unchanged
│   ├── security/
│   │   └── ApiKeyAuthenticationFilter.java           # moved; unchanged
│   └── config/
│       ├── RestSecurityConfiguration.java            # moved; gated; /api/** chain @Order(1)
│       └── IdentityRestApiAutoConfig.java            # NEW: @AutoConfiguration @ConditionalOnProperty importing controllers+security
└── src/main/resources/META-INF/spring/
    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports  # NEW: registers IdentityRestApiAutoConfig

identity-rest-app/                    # NEW thin app: runnable standalone REST-only deployment
├── build.gradle                      # org.springframework.boot plugin; depends on identity-rest-server
├── src/main/java/vg/identity/
│   └── IdentityApplication.java      # moved main() from identity-rest-service
└── src/main/resources/
    └── application.properties        # identity.rest.api.enabled=true (preserves current behavior) + encryption/email props

# identity-rest-service/              # REMOVED — split into identity-rest-server (lib) + identity-rest-app (runner)

identity-frontend-vaadin/             # Vaadin app — gains optional REST API
├── build.gradle                      # add implementation dependency on identity-rest-server
├── src/main/java/vg/identity/frontend/vaadin/config/
│   └── SecurityConfiguration.java    # Vaadin chain stays lower-priority than the /api/** chain
└── src/main/resources/
    ├── application.properties        # identity.rest.api.enabled=false (default, safe)
    └── application-<profile>.properties  # optional profile/property that flips it to true

identity-integration-tests/           # cross-module functional/integration + conformance tests
├── build.gradle                      # depend on identity-rest-server + identity-rest-client (replace identity-rest-service)
└── src/test/java/vg/identity/
    ├── rest/IdentityApplicationRestClientIntegrationTest.java   # existing (retarget to new modules)
    └── (new) vaadin-mode + embedded-vs-remote conformance tests

settings.gradle                       # include 'identity-rest-server' and 'identity-rest-app'; drop 'identity-rest-service'
```

**Structure Decision**: Split the current `identity-rest-service` app module into two:
`identity-rest-server` (a `java-library` holding the controllers + API-key security + auto-config,
guarded by `@ConditionalOnProperty("identity.rest.api.enabled")`) and `identity-rest-app` (a thin
Spring Boot runner). Both `identity-rest-app` and `identity-frontend-vaadin` depend on
`identity-rest-server`. This is preferred over (a) moving controllers into `identity-logic` — which
would force Spring MVC onto every logic consumer — and over (b) keeping one dual-purpose
`identity-rest-service` module, because a Spring Boot *application* module is awkward to consume as a
library (fat `bootJar`) and its `@SpringBootApplication` main sits in package `vg.identity`, which the
Vaadin app's component scan (`scanBasePackages = {"vg.identity", ...}`) would erroneously pick up. A
pure library with no `@SpringBootApplication` avoids both problems and mirrors the existing
`identity-rest-client` (client) / `identity-rest-server` (server) symmetry. See
[research.md](research.md) for the full comparison.

## Complexity Tracking

> No constitution violations — no entries required.
