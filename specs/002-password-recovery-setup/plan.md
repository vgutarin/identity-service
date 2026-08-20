# Implementation Plan: Password Recovery & Initial Setup

**Branch**: `002-password-recovery-setup` | **Date**: 2026-08-19 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-password-recovery-setup/spec.md`

## Summary

Add a self-service **password recovery** flow to the Vaadin identity app and confirm that **initial
password setup** for invited users is already served by the existing email-verification link.

Recovery reuses the existing secure action-token pipeline (`IdentityActionTokenService` /
`IdentityActionTokenProcessorService` / `OpaqueKey` / `IdentityActionTokenEntity`): a new
`RESET_PASSWORD` action type (appended to the ORDINAL-mapped `IdentityActionType`) is issued when an
**unauthenticated** user requests recovery for a **verified** email, delivered as a single-use,
time-limited link via the existing async email command queue (`SEND_EMAIL` → `EmailService`), and
consumed by a locked (`SELECT … FOR UPDATE`) processor step that sets a new Argon2-hashed password,
invalidates the user's other sessions, and hard-deletes the token. Two new anonymous Vaadin routes
(request + reset) plus a "forgot password?" link on `LoginView` complete the UI. Abuse is bounded by
the existing per-email cooldown **plus** a new per-IP/client request rate limit. The response to a
recovery request is always the same neutral confirmation, regardless of whether the email exists
(no account enumeration).

Initial password setup (US2) is **entered through the invitation's existing email-verification link**
(`IdentityUserEmailVerificationView` + `CredentialsForm` + `confirmEmail(actionKey, provisioning)`),
which already sets an Argon2 password on provisioning; this plan verifies that path for invited users
and adds no new setup-link surface (per FR-001b / FR-017).

No database schema change is required: the new action type is a new ORDINAL value in an existing
`INT` column, and every field the reset token needs (`principal`, `identityUserChannel`, `secret_hash`,
`created_at`, `expire_at`) already exists on `identity_action_token`.

## Technical Context

**Language/Version**: Java (Spring Boot 4.1.0, Spring Security 7, Jakarta EE), matching existing modules.

**Primary Dependencies**: Spring Boot 4.1.0 (web MVC, security, data-jpa), Spring Security 7,
Vaadin Flow 25.2.2 (`vaadin-spring-boot-starter`), Hibernate/JPA, Spring `MessageSource` (i18n),
`JavaMailSender`. Password hashing via the existing `DelegatingPasswordEncoder("argon2", …)` bean in
`IdentityLogicConfig`. No new external service is introduced (per spec Assumptions).

**Storage**: Existing relational store (MySQL 8) via JPA; schema owned by **Liquibase**
(`identity-logic/src/main/resources/db/liquibase/`, auto-included via `liquibase-changelog.yml`).
Reuses `identity_action_token`, `identity_user`, `identity_user_channel`, `identity_command`. **No new
table or column** (see Constitution Check + data-model.md). Token secret persisted only as SHA-256
`BINARY(32)`; password persisted as Argon2 hash inside an AES-GCM-encrypted (`@Convert`) BLOB column.

**Testing**: JUnit 5 + Mockito (unit), `@SpringBootTest`-based integration extending
`BaseIntegrationTest` (`@ActiveProfiles({"test","integration"})`, Testcontainers MySQL 8 via
`Mysql8ContainerStarter`, fixed `Clock` bean for deterministic expiry/cooldown). Email is asserted at
the **command-queue level** (a `SEND_EMAIL`/`QUEUED` `IdentityCommandEntity` is enqueued), not via a
live SMTP server.

**Target Platform**: JVM server application — the `identity-frontend-vaadin` web app.

**Project Type**: Multi-module web service + Vaadin web UI (Gradle multi-project). This feature touches
`identity-logic` (domain logic) and `identity-frontend-vaadin` (UI); it does **not** touch
`identity-rest-server` / `identity-rest-client` (web-only per FR-016).

**Performance Goals**: No throughput target. The recovery request path MUST run the **same work
regardless of email existence** so its latency does not reveal account existence (SC-002); email is
sent asynchronously via the command queue, so request latency is dominated by one blind-index lookup
either way.

**Constraints**: Neutral confirmation for every request outcome (existing / unknown / unverified /
rate-limited); single-use + time-limited link; constant-time secret verification (reuse
`OpaqueKey.secretMatches`); no secrets, raw links, or plaintext passwords in logs (FR-013); all new
user-facing strings keyed for `uk-UA` (default) + `en` with fallback (Principle VIII); new
`IdentityActionType` value MUST be **appended** (ORDINAL mapping).

**Scale/Scope**: Single running Vaadin instance is the current deployment topology; the per-IP rate
limiter and session-invalidation registry are therefore in-memory with a documented multi-instance
caveat (see research.md). Scope = one recovery request view, one reset view, one login-page link,
one new action type + mail factory + template, i18n keys, a rate limiter, and session invalidation on
password change. US2 is verification/coverage of the existing invitation → verify-email → set-password
path.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Assessment |
|-----------|------------|
| I. Data Safety First | PASS — recovery tokens are single-use, expiring, and stored only as SHA-256; completing a reset invalidates other sessions (FR-014); no new sensitive data path or store is added. |
| II. Encryption Everywhere | PASS — new password stored as Argon2 hash inside the existing AES-GCM `@Convert` column; token secret never stored in reversible form; email carries a link, not credentials. TLS remains a deployment requirement (unchanged). |
| III. Authentication & Authorization Rigor | PASS (by design) — the request + reset views are intentionally `@AnonymousAllowed` (a locked-out user cannot authenticate first). Authority to change the password derives **solely** from possession of the single-use, emailed token, which proves control of a *verified* email (FR-001a); the change is applied server-side under a pessimistic lock; everything else stays default-deny. |
| IV. Modern Defense-in-Depth | PASS — layered controls: per-email cooldown (FR-007) + per-IP/client rate limit (FR-007a) + bounded expiry (FR-006) + single-use consume-on-success (FR-005) + constant-time verify (FR-004) + no enumeration (FR-003). No deprecated crypto; reuses Argon2 + SHA-256 + `SecureRandom`. |
| V. Secure by Default & Auditable | PASS — neutral confirmation is the default and only response; security events (requested / consumed / changed / failed-or-expired) are logged structurally without secrets, raw links, or passwords (FR-013). |
| VI. Dual API Implementations | N/A / PASS — FR-016 scopes this to a user-facing web flow; **no public API surface is added**, so the embedded+remote obligation is not triggered. Domain logic still lives in `identity-logic` services (reusable) rather than in the UI, keeping the door open for a future API without duplicating logic. |
| VII. Code Quality & Test Discipline | PASS (with required work) — unit tests for the mail factory, rate limiter, reset-token issue/parse, and password application; integration tests (extending `BaseIntegrationTest`, fixed `Clock`) for issue+enqueue, cooldown, eligibility/enumeration parity, reset consume + password change + session invalidation, and expired/reused/tampered links. Green build + lint gate before merge. |
| VIII. Localization & Internationalization | PASS — every new string (request view, reset view, email subject+body) ships as a message key with `en` + `uk_UA` values; email template is bilingual like `confirm-email`; validation reuses the already-keyed `credentials.password.*` / `exception.user.password.weak`. |

**Result: PASS — no violations. Complexity Tracking section left empty.**

## Project Structure

### Documentation (this feature)

```text
specs/002-password-recovery-setup/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── recovery-ui-routes.md
│   └── reset-token-service.md
├── checklists/
│   └── requirements.md  # from /speckit-specify + /speckit-clarify
├── spec.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
identity-logic/src/main/java/vg/identity/
├── model/
│   ├── IdentityActionType.java              # MODIFY: append RESET_PASSWORD (ordinal 2)
│   └── IdentityAction.java                  # MODIFY: add ResetPasswordInfo record
├── service/
│   ├── IdentityActionTokenService.java      # MODIFY: requestPasswordReset(email, clientKey),
│   │                                        #         findResetPasswordActionInfo(key),
│   │                                        #         findResetPasswordActionForUpdate(key)
│   ├── IdentityActionTokenProcessorService.java # MODIFY: resetPassword(actionKey, rawPassword)
│   ├── IdentityUserService.java             # MODIFY: package-private setPassword(entity, rawPassword)
│   ├── ResetPasswordMailFactory.java        # NEW: bilingual reset-password email
│   └── RequestRateLimiter.java              # NEW: per-IP/client fixed-window limiter (in-memory)
├── IdentityActionTokenProperties.java       # MODIFY: add resetPasswordBaseUrl (default /reset/password/)
├── IdentityActionLinkBuilder.java           # MODIFY: add resetPasswordUri(actionKey)
└── resources/
    ├── db/liquibase/                        # NO CHANGE (no schema change)
    └── templates/email/
        └── reset-password.html.template     # NEW: bilingual (uk then en), ${webUrl}, subject header

identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/
├── auth/
│   ├── LoginView.java                       # MODIFY: add localized "forgot password?" link → recover route
│   ├── PasswordRecoveryRequestView.java     # NEW: @Route("recover/password"), @AnonymousAllowed
│   └── PasswordResetView.java               # NEW: @Route("reset/password/:id?"), @AnonymousAllowed
├── ui/
│   └── SetPasswordForm.java                 # NEW: password + confirm only (no display name) — or a
│                                            #      no-display-name mode on CredentialsForm
├── service/
│   └── IdentityActionLinkBuilderVaadin.java # MODIFY: build absolute reset URL from PasswordResetView route
├── config/
│   └── SecurityConfiguration.java           # MODIFY: SessionRegistry bean + permit recover/reset routes
└── resources/
    ├── messages.properties                  # MODIFY: password.recovery.* / password.reset.* keys (en)
    └── messages_uk_UA.properties            # MODIFY: same keys (uk-UA)

identity-logic/src/test/java/vg/identity/service/         # NEW unit + integration tests
identity-frontend-vaadin/src/test/java/...                # NEW view/flow tests
```

**Structure Decision**: Domain logic (token issue/consume, password change, mail, rate limiting) lives
in **`identity-logic`** behind services — mirroring the existing `CONFIRM_EMAIL` implementation — so it
stays out of the UI and could later back an API without a rewrite. UI (two anonymous routes + login
link + password form) lives in **`identity-frontend-vaadin`**, reusing `VaadinAuthenticationService`,
`LocalizationService`, and the `CredentialsForm` validation pattern. No REST module is touched.

## Complexity Tracking

> No constitutional violations — section intentionally empty.
