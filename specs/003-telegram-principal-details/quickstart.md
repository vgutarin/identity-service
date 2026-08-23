# Quickstart / Validation: Consent-Gated Principal From Telegram Data

**Feature**: 003-telegram-principal-details | **Date**: 2026-08-23

Validates the consent gate, the OIDC-compatible principal shape, and audit behavior end-to-end. See
[contracts/telegram-authentication.md](contracts/telegram-authentication.md) and
[data-model.md](data-model.md) for the contract and invariants.

## Prerequisites

- Java 21 toolchain; repo builds with the Gradle wrapper.
- REST API enabled: `identity.rest.api.enabled=true`.
- A registered application with a valid API key and a configured Telegram bot (as in the existing
  `identity-integration-tests` setup).
- A way to produce valid Telegram init data for that bot (existing test fixtures/helpers).

## Build & test

```bash
./gradlew :identity-logic:test :identity-rest-server:test :identity-rest-client:build :identity-integration-tests:test
```

Expected: all pass, including the new consent-gated scenarios below.

## Scenario 1 — No consent: identify without storing (US1)

1. `POST /api/v1/applications/me/authentications/telegram` with valid `telegramInitData` and
   `consentToKeepPersonalData: false`.
2. Expect **200** with `sub: null`, non-empty `name`, `permissions: []`, `consentGiven: false`.
3. Verify **no** user row was created for that Telegram id and `consentToKeepPersonalDataAt` is unset.

## Scenario 2 — Consent: establish the user (US2)

1. `POST …/telegram` with the same `telegramInitData` and `consentToKeepPersonalData: true`.
2. Expect **200** with non-null `sub`, `consentGiven: true`, and the application-scoped `permissions`.
3. Verify exactly **one** user was created and `consentToKeepPersonalDataAt` is set.
4. Re-submit (with or without consent): expect the **same** `sub`, no duplicate user.

## Scenario 3 — Permissions accuracy (US2)

1. Grant the user a known permission in the calling application's scope.
2. Resolve with consent; assert `permissions` exactly matches the granted set (no cross-application or
   cross-workspace entries). A user with no grants returns `permissions: []` with a non-null `sub`.

## Scenario 4 — Invalid data (edge)

1. `POST …/telegram` with invalid/unverifiable `telegramInitData` (consent `true` or `false`).
2. Expect **204 No Content**, no principal body, and **no** user persisted.

## Scenario 5 — OIDC claim shape (clarification)

1. Inspect the 200 body: identity fields use OIDC claim names — `sub`, `name` (and reserved `locale`).
2. Established principal has a present, non-null `sub`; pre-consent omits/nulls `sub` but still uses
   standard names for present claims.
3. Confirm the `locale` placeholder is **absent** (unpopulated by this feature).

## Scenario 6 — Audit (FR-022/FR-023)

1. With the `vg.identity.security.audit` logger routed to a capture sink, run Scenario 2.
2. Expect a consent-triggered creation event and an authentication event, both referencing the user by
   `sub`.
3. Assert audit output contains **no** raw `telegramInitData`, Telegram profile fields, or API key.

## Done / acceptance

- Scenarios 1–6 pass, covering Success Criteria SC-001…SC-010.
- No user/personal data is persisted in any no-consent path (SC-001).
- `sub`/`consentGiven` consistency holds in every response (SC-003, INV-1).
