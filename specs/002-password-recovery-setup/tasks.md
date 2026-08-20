---
description: "Task list for Password Recovery & Initial Setup"
---

# Tasks: Password Recovery & Initial Setup

**Input**: Design documents from `/specs/002-password-recovery-setup/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: Included and REQUIRED — constitution Principle VII mandates automated tests for
security-critical paths (authentication, tokens, credential changes). This entire feature is such a
path, so test tasks are not optional here.

**Organization**: Tasks are grouped by user story. US1 (recovery) is the MVP. US2 (initial setup)
verifies the existing email-verification path. US3 (re-request) refines US1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 / US2 / US3 (Setup, Foundational, Polish carry no story label)
- All paths are repo-relative.

## Path Conventions (this multi-module Gradle project)

- Logic main: `identity-logic/src/main/java/vg/identity/`
- Logic test: `identity-logic/src/test/java/vg/identity/`
- Logic resources: `identity-logic/src/main/resources/`
- Frontend main: `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/`
- Frontend test: `identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/`
- Frontend resources: `identity-frontend-vaadin/src/main/resources/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm a clean baseline before touching shared code. No new project scaffolding — the
modules already exist.

- [X] T001 Confirm baseline build/tests are green on branch `002-password-recovery-setup` by running `./gradlew :identity-logic:test :identity-frontend-vaadin:test`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared domain plumbing that the recovery stories (US1, US3) depend on. No UI or behavior
yet. (US2 rides the existing verification flow and does not depend on this phase.)

**⚠️ CRITICAL**: US1 and US3 cannot begin until this phase is complete.

- [X] T002 Append `RESET_PASSWORD` (ordinal 2) to the enum in `identity-logic/src/main/java/vg/identity/model/IdentityActionType.java` — MUST append (never insert/reorder); ORDINAL-mapped column
- [X] T003 [P] Add a `ResetPasswordInfo` record (validated action key) to `identity-logic/src/main/java/vg/identity/model/IdentityAction.java`, mirroring `ConfirmEmailInfo`
- [X] T004 [P] Add `resetPasswordBaseUrl` (default `/reset/password/`, `@NotBlank`) to `identity-logic/src/main/java/vg/identity/IdentityActionTokenProperties.java`
- [X] T005 [P] Add `URI resetPasswordUri(String actionKey)` to the `IdentityActionLinkBuilder` interface in `identity-logic/src/main/java/vg/identity/IdentityActionLinkBuilder.java` (and its logic-side fallback impl in `IdentityLogicConfig` if one exists)
- [X] T006 [P] Add package-private `void setPassword(IdentityUserEntity entity, String rawPassword)` (encode with the existing `argon2` `PasswordEncoder`, save) to `identity-logic/src/main/java/vg/identity/service/IdentityUserService.java`

**Checkpoint**: Shared reset-token plumbing compiles; recovery stories can begin.

---

## Phase 3: User Story 1 - Recover a forgotten password (Priority: P1) 🎯 MVP

**Goal**: An unauthenticated user with a verified email requests recovery, receives a single-use
time-limited link, sets a new policy-compliant password, and signs in — with no account enumeration
and abuse throttling in place.

**Independent Test**: Create a user with a known password + verified email; request recovery; assert a
`RESET_PASSWORD` token and a `SEND_EMAIL` command are created; consume the link; assert the new
password authenticates and the old one does not.

### Tests for User Story 1 (write first; ensure they FAIL before implementation) ⚠️

- [X] T007 [P] [US1] Unit test `ResetPasswordMailFactory` (subject from `<!-- subject: -->` header, `${webUrl}` substituted/escaped, bilingual body) in `identity-logic/src/test/java/vg/identity/service/ResetPasswordMailFactoryTest.java`
- [X] T008 [P] [US1] Unit test `RequestRateLimiter` (per-key window allow/deny) in `identity-logic/src/test/java/vg/identity/service/RequestRateLimiterTest.java`
- [X] T009 [P] [US1] Integration test: `requestPasswordReset` for a **verified** email creates exactly one `RESET_PASSWORD` token (principal+channel+`expireAt`) and one `SEND_EMAIL`/`QUEUED` command, in `identity-logic/src/test/java/vg/identity/service/IdentityActionTokenServiceIntegrationTest.java` (extend `BaseIntegrationTest`, fixed `Clock`)
- [X] T010 [P] [US1] Integration test: enumeration parity — unknown email and unverified channel produce **no** token and **no** command with no caller-visible difference (SC-002), in `identity-logic/src/test/java/vg/identity/service/IdentityActionTokenServiceIntegrationTest.java`
- [X] T011 [P] [US1] Integration test: per-email cooldown blocks a second request within the window (FR-007), same file
- [X] T012 [P] [US1] Integration test: `resetPassword` happy path changes password (new authenticates, old fails), deletes the token (FR-005/010), AND assert the persisted `password` is an `argon2`-prefixed one-way hash — never the raw input (FR-009/SC-005) — in `identity-logic/src/test/java/vg/identity/service/IdentityActionTokenProcessorServiceIntegrationTest.java`
- [X] T013 [P] [US1] Integration test: weak password throws `exception.user.password.weak` and leaves the token usable (FR-008); expired/reused/tampered link rejected (FR-004/005/006/011), same file
- [X] T014 [P] [US1] Frontend test: `PasswordRecoveryRequestView` shows the neutral confirmation (and never leaks) — in `PasswordRecoveryRequestViewTest`; `PasswordResetView` renders missing / invalid-or-expired / valid (form) states — in `PasswordResetViewTest`. Uses Vaadin's first-party browserless testing (`com.vaadin:browserless-test-spring`, `SpringBrowserlessTest`) with mocked domain services and an H2/Liquibase test profile (`src/test/resources/application-test.properties`).
- [X] T014a [P] [US1] Localization completeness test: assert every new `password.recovery.*` / `password.reset.*` / `login.forgotPassword.link` key exists in BOTH `messages.properties` and `messages_uk_UA.properties`, and that a missing non-default key falls back to the default locale (no raw key surfaced) — SC-006/FR-012 — in `identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/service/PasswordRecoveryLocalizationTest.java`

### Implementation for User Story 1

- [X] T015 [P] [US1] Create bilingual (uk→en) `identity-logic/src/main/resources/templates/email/reset-password.html.template` with a `<!-- subject: -->` header and a single `${webUrl}` placeholder (no Telegram variant)
- [X] T016 [P] [US1] Implement `ResetPasswordMailFactory` (load template, build `EmailMessage`) in `identity-logic/src/main/java/vg/identity/service/ResetPasswordMailFactory.java`
- [X] T017 [P] [US1] Implement in-memory `RequestRateLimiter` (per-key fixed-window) in `identity-logic/src/main/java/vg/identity/service/RequestRateLimiter.java`, reading defaults **10 requests / `PT10M` window** from config keys `identity.action-token.reset-rate-limit.max-requests` and `.window` (FR-007a)
- [X] T018 [US1] Add `requestPasswordReset(String email, String clientKey)` to `identity-logic/src/main/java/vg/identity/service/IdentityActionTokenService.java` — the `clientKey` is the caller-supplied client identifier (the logic layer MUST NOT read the servlet request); canonicalize email, rate-limit by `clientKey` (T017), verified-channel eligibility (FR-001a), cooldown reuse of `existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual`, issue token via `OpaqueKey`, build link via `resetPasswordUri`, enqueue via `ResetPasswordMailFactory` → `IdentityCommandService.enqueue`; always returns `void` on a single code path with no data-dependent short-circuit (FR-003/SC-002) (depends on T002–T006, T016, T017)
- [X] T019 [US1] Add `findResetPasswordActionInfo(actionKey)` and package-private `findResetPasswordActionForUpdate(actionKey)` (parse, `findByIdForUpdate` lock, constant-time `OpaqueKey.secretMatches`, expiry + type checks) to `identity-logic/src/main/java/vg/identity/service/IdentityActionTokenService.java`
- [X] T020 [US1] Add `ResetResult resetPassword(actionKey, rawPassword)` to `identity-logic/src/main/java/vg/identity/service/IdentityActionTokenProcessorService.java` — locked lookup (T019), `PasswordPolicy.requireStrong`, `IdentityUserService.setPassword` (T006), `consumeAction`, return principal for sign-in/session-invalidation (depends on T006, T019)
- [X] T021 [US1] Implement `resetPasswordUri` absolute-URL building from the `PasswordResetView` route in `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/service/IdentityActionLinkBuilderVaadin.java` (single source of truth for the route)
- [X] T022 [P] [US1] Create `SetPasswordForm` (password + confirm only; reuse `PasswordPolicy::isStrong` and `credentials.password.*` keys; expose `resetSubmit()`) in `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/ui/SetPasswordForm.java`
- [X] T023 [US1] Create `PasswordRecoveryRequestView` (`@Route("recover/password")`, `@AnonymousAllowed`): email field → resolve the client key (IP from `VaadinServletRequest.getRemoteAddr()`, honoring a trusted forwarded header only behind a trusted proxy) → `requestPasswordReset(email, clientKey)` → always show `password.recovery.confirmation`, in `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/auth/PasswordRecoveryRequestView.java` (depends on T018)
- [X] T024 [US1] Create `PasswordResetView` (`@Route("reset/password/:id?")`, `@AnonymousAllowed`): read action key, `findResetPasswordActionInfo`, render `SetPasswordForm`, submit → `resetPassword`, then sign in via `VaadinAuthenticationService` + redirect `/` (FR-015), in `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/auth/PasswordResetView.java` (depends on T020, T022)
- [X] T025 [US1] Add a `SessionRegistry` bean, invalidate the reset user's other sessions on success (FR-014), and permit the `recover/password` + `reset/password/**` routes in `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/config/SecurityConfiguration.java`
- [X] T026 [US1] Add a localized `login.forgotPassword.link` linking to `recover/password` on `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/auth/LoginView.java`
- [X] T027 [US1] Add `password.recovery.*`, `password.reset.*`, and `login.forgotPassword.link` keys to `identity-frontend-vaadin/src/main/resources/messages.properties` AND `messages_uk_UA.properties` (both locales, per Principle VIII) — see key list in [contracts/recovery-ui-routes.md](contracts/recovery-ui-routes.md)
- [X] T028 [US1] Add structured audit logging (reset requested / link consumed / password changed / failed-or-expired) with no secrets, raw links, or plaintext passwords (FR-013) across `IdentityActionTokenService` and `IdentityActionTokenProcessorService`

**Checkpoint**: US1 fully functional — a forgotten-password reset works end to end with enumeration
protection, cooldown, and per-IP rate limiting. MVP deliverable.

---

## Phase 4: User Story 2 - Set an initial password (Priority: P2)

**Goal**: An invited user reached via a pending workspace email channel sets their first password
through the **existing** email-verification link (no new surface).

**Independent Test**: Create a pending workspace email-channel invitation with no password; open the
invitation's `verify/email/:id?` link; set a compliant password; confirm the invited user can sign in —
and that no `RESET_PASSWORD` token or separate setup surface was involved.

### Tests for User Story 2 ⚠️

- [X] T029 [P] [US2] Integration test: an invited pending-channel user is provisioned with an Argon2 password via `confirmEmail(actionKey, UserProvisioningDetails(...))` and can authenticate; assert no `RESET_PASSWORD` token/surface is used (FR-001b/FR-017). Added as `invitedPendingChannelUser_setsInitialPasswordViaConfirmEmail_withoutResetPasswordToken` in the canonical `identity-logic/src/test/java/vg/identity/service/IdentityActionTokenProcessorServiceIntegrationTest.java` (not a separate phantom-named file).

### Implementation for User Story 2

- [X] T030 [US2] Verify (and close any gap in) the invited-user path so a **pending workspace email channel** receives a `CONFIRM_EMAIL` link and `IdentityUserEmailVerificationView` + `CredentialsForm` set the initial password — inspect `identity-logic/src/main/java/vg/identity/service/IdentityActionTokenService.java` (`confirm`) and the workspace/invitation issuance path; wire the confirm-email issuance for pending invitation channels if it is not already triggered — **VERIFIED, no change needed**: `IdentityWorkspaceService.addUser` already creates a pending email channel and calls `actionTokenService.confirm(channel)` (issues CONFIRM_EMAIL + enqueues the verify email); provisioning through `IdentityUserEmailVerificationView`/`CredentialsForm` sets the initial password.
- [X] T031 [US2] Confirm the provisioning prompt/labels for invited users are localized (reuse `email.verification.provisioning.prompt` and `credentials.*`); add any missing key to both `messages.properties` and `messages_uk_UA.properties` — **VERIFIED, no change needed**: `email.verification.provisioning.prompt` and the `credentials.*` keys already exist in both bundles.

**Checkpoint**: Invited users can set an initial password via the verification link; US1 remains intact.

---

## Phase 5: User Story 3 - Request a fresh recovery link (Priority: P3)

**Goal**: A user whose recovery link expired or was used can request a new one from the recovery
surface and complete the flow, while cooldown still applies.

**Independent Test**: Expire (or consume) a recovery link; request a new link for the same email;
confirm the new link works and the old one is refused; confirm a second request within the cooldown
sends no new link.

### Tests for User Story 3 ⚠️

- [X] T032 [P] [US3] Integration test: after expiry/use, a fresh `requestPasswordReset` issues a new usable token while the old key is refused; a second request within `request-cooldown` issues nothing (FR-007). Added as `requestPasswordReset_afterPreviousLinkConsumed_issuesFreshUsableToken` in the canonical `IdentityActionTokenServiceIntegrationTest.java` (reissue-after-consume); the cooldown-blocks case is covered by `requestPasswordReset_whenWithinCooldown_issuesOnlyOnce` and old-key-refused by the processor's `resetPassword_whenAlreadyUsed_secondAttemptFails`.
- [X] T033 [P] [US3] Frontend test: the `PasswordResetView` missing and invalid-or-expired states offer a `RouterLink` back to `recover/password` — asserted in `PasswordResetViewTest` (`missingKey_*`, `invalidOrExpiredKey_*`), and that the valid state shows the form with no such link.

### Implementation for User Story 3

- [X] T034 [US3] In `PasswordResetView` (missing / invalid-or-expired / mid-flow-invalidated branches), render a localized `RouterLink` back to `recover/password` (`password.reset.requestNew`, already in both bundles) so the user can request a fresh link, in `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/auth/PasswordResetView.java`

**Checkpoint**: All three stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T035 [P] Document the recovery flow (routes, config, security properties, deployment notes) in `identity-frontend-vaadin/docs/password-recovery.md` (new, self-contained; not folded into the pre-existing untracked `identity-logic/README.md`).
- [X] T036 Run the [quickstart.md](quickstart.md) validation scenarios and record results — **automated (authoritative) scenarios pass**: `identity-logic` 451 tests, `identity-frontend-vaadin` 5 tests, and full `./gradlew check` green. The manual UI smoke test (steps 1–4) was NOT executed here — it needs a running app + DB; deferred to manual QA.
- [X] T037 [P] Security pass (FR-013 / SC-002) — **verified**: every recovery log statement references only `tokenId` or a static message/exception; none logs a secret, raw action key, raw link, or plaintext password. `requestPasswordReset` runs a single code path with no data-dependent early exception and sends email asynchronously (best-effort timing parity, as documented in the reworded SC-002).
- [X] T038 Run the full gate: `./gradlew build` green before merge — **PASS** (BUILD SUCCESSFUL, all modules, incl. Testcontainers integration tests and the Vaadin production frontend build). Fixed the pre-existing root `:bootJar` failure: the Spring Boot plugin is no longer applied to the aggregator root (`apply false`), and `bootJar` is enabled only for the runnable app modules (`identity-rest-app`, `identity-frontend-vaadin`); library modules skip it and publish plain jars.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: none.
- **Foundational (Phase 2)**: after Setup. Blocks US1 and US3.
- **US1 (Phase 3)**: after Foundational.
- **US2 (Phase 4)**: independent of Foundational/US1 (rides existing verification flow) — can run any time after Setup.
- **US3 (Phase 5)**: after US1 (extends the recovery request surface + reset view).
- **Polish (Phase 6)**: after all targeted stories.

### User Story Dependencies

- **US1 (P1)**: depends only on Foundational. Delivers the MVP.
- **US2 (P2)**: fully independent (existing flow) — no dependency on US1.
- **US3 (P3)**: depends on US1 (reuses its request surface, reset view, and token type).

### Within Each User Story

- Tests are written first and must FAIL before implementation.
- Domain/service tasks precede view tasks; views precede session/security wiring that references them.
- i18n keys land in the same change as the UI that uses them (Principle VIII).

### Parallel Opportunities

- Foundational: T003, T004, T005, T006 are [P] (distinct files); T002 first (others may reference the new enum).
- US1 tests T007–T014a are all [P] (distinct test files) once Foundational is done.
- US1 implementation: T015, T016, T017, T022 are [P] (distinct files) before the service/view tasks that depend on them (T018–T021, T023–T024).
- US2 (T029–T031) can proceed in parallel with US1 by another developer.

---

## Parallel Example: User Story 1

```bash
# After Foundational (T002–T006), launch US1 test authoring in parallel:
Task: "Unit test ResetPasswordMailFactory (ResetPasswordMailFactoryTest.java)"
Task: "Unit test RequestRateLimiter (RequestRateLimiterTest.java)"
Task: "Integration test requestPasswordReset issue+enqueue (IdentityActionTokenServiceIntegrationTest.java)"
Task: "Integration test enumeration parity (IdentityActionTokenServiceIntegrationTest.java)"
Task: "Integration test resetPassword happy path (IdentityActionTokenProcessorServiceIntegrationTest.java)"

# Then launch independent US1 building blocks in parallel:
Task: "reset-password.html.template (bilingual)"
Task: "ResetPasswordMailFactory.java"
Task: "RequestRateLimiter.java"
Task: "SetPasswordForm.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup.
2. Phase 2: Foundational (enum, record, properties, link-builder, setPassword).
3. Phase 3: US1 — recovery request + reset + rate limit + session invalidation + i18n + audit.
4. **STOP and VALIDATE**: run the US1 tests + manual smoke from quickstart; this is a shippable MVP.

### Incremental Delivery

1. Setup + Foundational → shared plumbing ready.
2. US1 → forgotten-password recovery (MVP) → demo.
3. US2 → invited-user initial setup verified via existing verification link → demo (can be done in parallel).
4. US3 → re-request fresh link → demo.

### Parallel Team Strategy

- Developer A: Foundational → US1.
- Developer B: US2 in parallel (independent existing-flow path).
- US3 follows US1 (same developer or hand-off).

---

## Notes

- `IdentityActionType`, `IdentityCommandType`, `IdentityCommandStatus` are ORDINAL-mapped — **append only**.
- **No Liquibase migration** is required (ordinal 2 fits the existing `action_type INT` column; all needed token fields already exist).
- Integration tests extend `BaseIntegrationTest` (`@SpringBootTest`, Testcontainers MySQL 8, fixed `Clock`); email is asserted at the command-queue level (`SEND_EMAIL`/`QUEUED`), not via live SMTP.
- In-memory `RequestRateLimiter` and `SessionRegistry` are single-instance-scoped by design (research.md Decisions 3 & 4); the per-email cooldown is DB-backed and cluster-correct.
- Commit after each task or logical group. Do not disable/skip tests to make the build pass.
