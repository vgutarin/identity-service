# Implementation Plan: Consent-Gated Principal From Telegram Data

**Branch**: `003-telegram-principal-details` | **Date**: 2026-08-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-telegram-principal-details/spec.md`

## Summary

Turn the existing Telegram authentication API operation into a **consent-gated** one that returns a
single, OIDC-compatible **principal** object. Before consent to keep personal data is given, the
service stores nothing and returns a principal with a null subject (`sub`); when consent is given it
creates the user and returns a principal with a stable `sub` plus the caller-application's
permissions. The response maps identity fields to OIDC standard claims (`sub`, `name`, reserved
`locale`) and carries permissions and the consent indicator as non-standard fields. Consent-triggered
user creation and the authentication are audited without logging secrets or raw Telegram data.

Technical approach: reshape the endpoint's response record into the OIDC-compatible principal, add a
consent-aware resolution path in `identity-logic` that persists a user (setting
`consentToKeepPersonalDataAt`) only when consent is granted, and emit structured security-audit
records through the existing `vg.identity.security.audit` logger. Full OIDC protocol support and any
population of the `locale` placeholder are explicitly deferred (see spec "Out of Scope / Deferred").

## Technical Context

**Language/Version**: Java 21 (`sourceCompatibility = '21'`)

**Primary Dependencies**: Spring Boot (web, data-jpa, security), Lombok, Jackson (JSON), JPA/Hibernate

**Storage**: Existing relational store via JPA (`IdentityUserEntity`, Telegram channel binding); H2 in
tests. No new tables — reuses `IdentityUserEntity.consentToKeepPersonalDataAt`.

**Testing**: JUnit 5 + AssertJ; Mockito for unit tests; `@SpringBootTest` integration tests;
`identity-integration-tests` drives the typed `identity-rest-client` end-to-end.

**Target Platform**: Linux server (Spring Boot service); REST API gated by `identity.rest.api.enabled`.

**Project Type**: Multi-module backend web service (Gradle). Modules touched: `identity-api`,
`identity-logic`, `identity-rest-server`, `identity-rest-client`, `identity-integration-tests`.

**Performance Goals**: No new targets; parity with the existing endpoint. Resolution is a single
verification + one indexed lookup (+ one insert only on consent-triggered creation).

**Constraints**: Consent-before-storage (no user persisted without consent); TLS in transit; no
secrets or raw Telegram personal data in logs/audit; permissions scoped to the calling application;
`sub` stable and never reassigned.

**Scale/Scope**: One API operation reshaped; one response type; small blast radius across ~6 usage
sites (service, controller, client, and their tests) plus a shared `PERMISSIONS_SCOPE` constant.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment | Status |
|-----------|-----------|--------|
| I. Data Safety First | Feature's core purpose: no user/personal data persisted until consent; corrects the current unconditional-provisioning path. | ✅ Pass (strengthens) |
| II. Encryption Everywhere | Reuses existing at-rest storage and TLS transport; adds no plaintext data path, no new secrets. | ✅ Pass |
| III. AuthN/AuthZ Rigor | Operation stays API-key authenticated (existing filter); permissions resolved server-side and scoped to the calling application; default-deny (empty permission set). | ✅ Pass |
| IV. Modern Defense-in-Depth | Reuses existing Telegram init-data verification (input validation); no deprecated algorithms; least-privilege unchanged. | ✅ Pass |
| V. Secure by Default & Auditable | Consent-gating is the new safe default; consent-triggered creation + authentication audited via `vg.identity.security.audit`, with no secrets/PII (FR-022, FR-023). | ✅ Pass |
| VIII. Localization & Internationalization | No new user-facing i18n behavior; the reserved `locale` claim, when later populated, MUST be a BCP-47 tag (FR-011). | ✅ Pass (N/A now) |

No violations → Complexity Tracking left empty.

## Project Structure

### Documentation (this feature)

```text
specs/003-telegram-principal-details/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── telegram-authentication.md
├── checklists/
│   └── requirements.md  # From /speckit-specify + /speckit-clarify
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
identity-api/
└── src/main/java/vg/identity/
    ├── model/
    │   └── IdentityApplicationUserPrincipal.java   # RESHAPE → OIDC-compatible principal (sub/name/permissions/consentGiven/locale)
    └── service/
        └── IdentityApplicationApi.java             # CHANGE authenticateTelegram signature (add consent)

identity-logic/
└── src/main/java/vg/identity/service/
    ├── IdentityApplicationApiService.java          # consent-gated resolution + principal assembly + audit
    └── IdentityApplicationUserProvisioningService.java  # consent-aware resolve/provision (no create without consent)
    (+ audit emission via "vg.identity.security.audit" logger; reuse claim/user/channel services)

identity-rest-server/
└── src/main/java/vg/identity/rest/server/controller/
    └── IdentityApplicationController.java          # accept consent on the request; return principal (200) or 204

identity-rest-client/
└── src/main/java/vg/identity/rest/v1/
    └── IdentityApplicationApiRestClient.java       # mirror new request/response contract

identity-integration-tests/
└── src/test/java/vg/identity/rest/
    └── IdentityApplicationRestClientIntegrationTest.java  # consent-gated end-to-end scenarios

# Unit/contract tests updated alongside: IdentityApplicationApiServiceTest, IdentityApplicationControllerTest,
# claim tests referencing PERMISSIONS_SCOPE, and Vaadin admin constant usage.
```

**Structure Decision**: Existing multi-module Gradle backend; no new modules. The public contract
lives in `identity-api` (the principal record + API interface) and is transported by
`identity-rest-server` / `identity-rest-client`. Consent-gating and audit logic live in
`identity-logic` where user creation happens, keeping the safety gate at the point of persistence.
