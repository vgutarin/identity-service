---
description: "Task list for Consent-Gated Principal From Telegram Data"
---

# Tasks: Consent-Gated Principal From Telegram Data

**Input**: Design documents from `/specs/003-telegram-principal-details/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/telegram-authentication.md](contracts/telegram-authentication.md)

**Tests**: Included. This is a breaking behavioral change to an existing, test-covered endpoint; the existing unit/contract/integration tests must be updated and new scenarios added (matches repo convention).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 / US2 / US3 / US4 (from spec.md)
- Exact file paths included per task

## Path notes

Multi-module Gradle backend. Public contract in `identity-api`; transport in `identity-rest-server` / `identity-rest-client`; consent + audit logic in `identity-logic`; end-to-end in `identity-integration-tests`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm scope; no new modules or dependencies.

- [X] T001 Confirm no new Gradle dependencies are required (Jackson/spring-web/security already present) and record the accepted breaking-change scope (reshaped principal + JSON request) in [plan.md](plan.md); verify Telegram init-data test fixtures already exist in `identity-integration-tests/src/test/java/vg/identity/rest/`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared contract types and interface signature that BOTH US1 and US2 depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Reshape the principal record to the OIDC-compatible shape (`sub` nullable, `name`, `permissions` as `Set<String>`, `consentGiven` boolean) in `identity-api/src/main/java/vg/identity/model/IdentityApplicationUserPrincipal.java`; kept the `PERMISSIONS_SCOPE` constant (used by Vaadin admin + claim tests). **Deviation (per user decision):** the optional `locale` field is **deferred**, not added now — documented in the record's Javadoc as a reserved, additive future OIDC claim. This keeps `identity-api` Jackson-free: default serialization renders `sub` present-and-null and there are no optional fields to omit, so no `@JsonInclude` / jackson-annotations dependency is needed.
- [X] T003 [P] Create the request DTO `TelegramAuthenticationRequest(String telegramInitData, boolean consentToKeepPersonalData)` in `identity-api/src/main/java/vg/identity/model/TelegramAuthenticationRequest.java` (consent defaults to `false` when absent).
- [X] T004 Change `authenticateTelegram` to accept consent and return the reshaped principal in `identity-api/src/main/java/vg/identity/service/IdentityApplicationApi.java` — depends on T002. **Refined in Phase 3:** the method takes the `TelegramAuthenticationRequest` DTO (`authenticateTelegram(TelegramAuthenticationRequest request)`) rather than `(String, boolean)`, so the Spring HTTP-interface client can send a single `@RequestBody` and the controller/client/embedded impl share one shape.

**Checkpoint**: Contract types compile; downstream modules will not compile until US1/US2 update their call sites (expected).

---

## Phase 3: User Story 1 - Identify without storing (Priority: P1) 🎯 MVP

**Goal**: Given Telegram data without consent, return a principal with `name` (from the Telegram profile), `sub: null`, empty `permissions`, `consentGiven: false`, and persist nothing.

**Independent Test**: POST valid init data with `consentToKeepPersonalData:false` → 200 with `sub:null`, non-empty `name`, `permissions:[]`; assert no user row created and `consentToKeepPersonalDataAt` unset (quickstart Scenario 1).

### Tests for User Story 1

- [X] T005 [P] [US1] Unit tests in `identity-logic/src/test/java/vg/identity/service/IdentityApplicationApiServiceTest.java`: no-consent + not-found → pre-consent principal (`sub` null, `name` from `TelegramUserPrincipal.displayName()`, empty permissions, `consentGiven` false), provisioning create NOT invoked; already-established (consent recorded) → subject + scoped permissions even when the request flag is false; invalid data + consent true → empty, nothing touched.
- [X] T006 [P] [US1] Unit tests for the no-create path in `identity-logic/src/test/java/vg/identity/service/IdentityApplicationUserProvisioningServiceTest.java`: `findTelegramUser` returns the bound user without a transaction, or empty with no creation. (Existing `resolveTelegramUser` tests retained; that method is reworked in US2.)
- [X] T007 [P] [US1] Controller test in `identity-rest-server/src/test/java/vg/identity/rest/server/controller/IdentityApplicationControllerTest.java`: JSON `TelegramAuthenticationRequest` accepted; resolved → 200 body; empty → 204.
- [X] T008 [P] [US1] Integration test in `identity-integration-tests/src/test/java/vg/identity/rest/IdentityApplicationRestClientIntegrationTest.java`: no-consent call returns pre-consent principal (`sub` null, `name`, empty permissions, `consentGiven` false) and persists no user/channel. (Old auto-provision "stable principal" test removed; the establish/stable-sub test is added in US2 / T016.)

### Implementation for User Story 1

- [X] T009 [US1] Added `findTelegramUser` (lookup-only, never creates) in `identity-logic/src/main/java/vg/identity/service/IdentityApplicationUserProvisioningService.java`. (Old `resolveTelegramUser` create path left intact for US2 to rework in T017.)
- [X] T010 [US1] Rewrote `authenticateTelegram(TelegramAuthenticationRequest)` in `identity-logic/src/main/java/vg/identity/service/IdentityApplicationApiService.java`: verify init data (empty→204), established-existing → subject principal (read-only), no-consent/not-established → pre-consent principal, nothing persisted. **The consent-triggered write path is a documented TODO deferred to US2 (T017-T019); interim safe fallback = pre-consent principal (never over-stores).**
- [X] T011 [US1] Endpoint now consumes JSON `TelegramAuthenticationRequest` → 200 / 204 in `identity-rest-server/src/main/java/vg/identity/rest/server/controller/IdentityApplicationController.java`.
- [X] T012 [US1] Mirrored the JSON request/response contract in `identity-rest-client/src/main/java/vg/identity/rest/v1/IdentityApplicationApiRestClient.java`.

**Checkpoint**: US1 delivered and green. NOTE: `IdentityApplicationClaimIntegrationTest` is `@Disabled` until US2 (it needs consent-triggered user creation); re-enable in T016.

---

## Phase 4: User Story 2 - Establish the user on consent (Priority: P1)

**Goal**: With consent, create the user (set `consentToKeepPersonalDataAt`), return a principal with a stable `sub` and application-scoped `permissions`; idempotent on re-submit; audit creation + authentication.

**Independent Test**: POST valid init data with `consentToKeepPersonalData:true` → 200 with non-null `sub`, `consentGiven:true`, correct `permissions`; exactly one user created; re-submit returns same `sub` (quickstart Scenarios 2–3, 6).

### Tests for User Story 2

- [X] T013 [P] [US2] Unit tests for `establishTelegramUser` in `identity-logic/src/test/java/vg/identity/service/IdentityApplicationUserProvisioningServiceTest.java`: not-found+consent creates one user with `consentToKeepPersonalDataAt` set + binds; already-consented → returned unchanged (no write/create); legacy null-consent → backfills consent without creating.
- [X] T014 [P] [US2] Unit tests for the consent branch in `identity-logic/src/test/java/vg/identity/service/IdentityApplicationApiServiceTest.java`: consent+new → non-null `sub`, `consentGiven:true`, `permissions` from `findClaimsByScope(...).get(PERMISSIONS_SCOPE)`, audits `userProvisioned`+`authenticationSucceeded`; consent+legacy → backfill, `authenticationSucceeded` only (no `userProvisioned`).
- [X] T015 [P] [US2] Audit test in `identity-logic/src/test/java/vg/identity/service/TelegramAuthenticationAuditorTest.java`: creation + authentication emit structured events on the `vg.identity.security.audit` logger referencing `sub` only, asserting no raw init data / profile fields / credentials (FR-022, FR-023). (Logback `ListAppender` capture.)
- [X] T016 [P] [US2] Integration test in `identity-integration-tests/src/test/java/vg/identity/rest/IdentityApplicationRestClientIntegrationTest.java`: consent call establishes user; `sub` stable across re-submits (and when a later request omits consent); permissions match granted scope. Also **re-enabled** `IdentityApplicationClaimIntegrationTest` (cross-application/workspace isolation) now that consent creates users.

### Implementation for User Story 2

- [X] T017 [US2] Added `establishTelegramUser` (create-on-consent: anonymous entity + Telegram binding with `consentToKeepPersonalDataAt` set; idempotent for existing users; backfills consent on legacy no-consent rows) in `identity-logic/src/main/java/vg/identity/service/IdentityApplicationUserProvisioningService.java`. Injected `IdentityUserRepository` + `Clock`. (Old `resolveTelegramUser` left in place, now unused by the API path.)
- [X] T018 [US2] Created `TelegramAuthenticationAuditor` (logger `vg.identity.security.audit`, sanitized, `sub`-only, no PII/secrets) in `identity-logic/src/main/java/vg/identity/service/TelegramAuthenticationAuditor.java`.
- [X] T019 [US2] Completed the consent branch in `identity-logic/src/main/java/vg/identity/service/IdentityApplicationApiService.java`: on consent calls `establishTelegramUser`, builds the principal with `sub` + scoped `permissions` + `consentGiven:true`, records authentication, and emits the creation audit (only when a new user was created) plus the authentication audit.

**Checkpoint**: Full consent gate works end-to-end; SC-001…SC-003, SC-006, SC-008, SC-010 verified. All modules green.

---

## Phase 5: User Story 3 - Reserve the locale placeholder (Priority: P2)

**Goal**: The principal reserves an OIDC `locale` placeholder that is always omitted (unpopulated) by this feature.

**Independent Test**: Inspect a 200 body → no `locale` key present; `sub`/`name` use OIDC claim names (quickstart Scenario 5).

- [X] T020 [P] [US3] Added a serialization contract test in `identity-rest-server/src/test/java/vg/identity/rest/server/controller/IdentityApplicationControllerTest.java` (`principal_usesOidcClaimNamesAndReservesLocaleAsAbsent`): identity fields serialize under OIDC claim names (`sub`, `name`), non-standard fields (`permissions`, `consentGiven`) don't collide, **`locale` is never emitted**, and a pre-consent principal round-trips with a null `sub`. **Deviation (per user decision, see T002):** the `locale` field was deferred, so the test asserts its *absence* rather than a reserved-but-null field. Uses Jackson 3 (`tools.jackson`, Spring Boot 4.1).

**Checkpoint**: SC-005, SC-009 (claim-shape) verifiable.

---

## Phase 6: User Story 4 - Extensible without breaking consumers (Priority: P3)

**Goal**: Optional fields are omitted when empty; adding a new optional field does not break existing consumers.

**Independent Test**: Deserialize a principal that includes an unknown/extra optional field with a client reading only defined fields → succeeds and ignores it.

- [X] T021 [P] [US4] Added a forward-compatibility test `principal_toleratesUnknownFutureClaims_soAddingOptionalFieldsDoesNotBreakConsumers` in `identity-rest-server/src/test/java/vg/identity/rest/server/controller/IdentityApplicationControllerTest.java`: a JSON payload carrying unknown future claims (incl. `locale`) deserializes into the current record via an unknown-tolerant mapper (as Spring Boot configures the client), ignoring the extras (FR-016 / SC-007). **Deviations:** (1) placed in the rest-server test rather than the integration file T021 named — faithfully testing this needs a crafted payload the live server won't emit, so a deserialization test co-located with the Phase-5 serialization test is cleaner; (2) the "optional fields omitted when empty" clause is already covered by the Phase-5 `locale`-absence assertion (the `locale` field was deferred, so there is no `@JsonInclude` to exercise).

**Checkpoint**: SC-004 (single-response greet), SC-007 (additive-safe) verifiable.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T022 [P] Verified no remaining compile breakage from the reshape at non-story call sites: `:identity-frontend-vaadin:compileJava` succeeds (Vaadin admin `IdentityApplicationClaims.java` uses only the retained `PERMISSIONS_SCOPE` constant) and the claim tests compile/pass.
- [X] T023 [P] Documented the endpoint contract + breaking change / consumer migration (old `text/plain` unconditional-provisioning contract → consent-gated JSON principal) and the new Telegram audit events in `identity-rest-server/README.md`.
- [X] T024 Ran `./gradlew :identity-frontend-vaadin:compileJava :identity-logic:test :identity-rest-server:test :identity-rest-client:build :identity-integration-tests:test` → BUILD SUCCESSFUL. [quickstart.md](quickstart.md) Scenarios 1–6 are covered by the passing tests (no-consent + establish integration tests, controller 200/204, OIDC serialization, forward-compat, and the auditor test for Scenario 6).

---

## Dependencies & Execution Order

- **Setup (T001)** → **Foundational (T002–T004)** → **US1 (T005–T012)** → **US2 (T013–T019)** → **US3 (T020)** / **US4 (T021)** → **Polish (T022–T024)**.
- US2 depends on US1: both branch the same `authenticateTelegram` method and provisioning service; US2's create path builds on US1's consent-aware entry point (T009) and no-consent method (T010).
- US3 and US4 depend only on the Foundational reshape (T002) and can run in parallel with each other after US2.
- Within Foundational: T002 and T003 are [P] (different files); T004 depends on T002.
- Within each story, the test tasks marked [P] touch different files and can run together; implementation tasks that share a file are sequential.

## Parallel Execution Examples

- **Foundational**: T002 and T003 together.
- **US1 tests**: T005, T006, T007, T008 together (different test files), before/against implementation.
- **US2 tests**: T013, T014, T015, T016 together.
- **After US2**: T020 (US3) and T021 (US4) together; T022 and T023 (polish) together.

## Implementation Strategy

- **MVP = Setup + Foundational + US1** — delivers the privacy-preserving, no-storage principal (the constitution-critical fix) as an independently shippable increment.
- **Increment 2 = US2** — consent-triggered establishment + permissions + audit (completes the consent gate).
- **Increment 3 = US3 + US4** — locale placeholder and extensibility guarantees.
- **Finish = Polish** — migration docs, cross-module compile, full build + quickstart.
