---
description: "Task list for Consent-Gated Principal From Telegram Data"
---

# Tasks: Consent-Gated Principal From Telegram Data

**Input**: Design documents from `/specs/003-telegram-principal-details/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/telegram-authentication.md](contracts/telegram-authentication.md)

**Tests**: Included. This is a test-covered endpoint; existing unit/contract/integration tests must be updated and new assertions added (repo convention, see [TESTING.md](../../TESTING.md)).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 / US2 / US3 / US4 (from spec.md)
- Exact file paths included per task

## Path notes

Multi-module Gradle backend. Public contract in `identity-api`; transport in `identity-rest-server` / `identity-rest-client`; consent + audit logic in `identity-logic`; end-to-end in `identity-integration-tests`.

## Current state (2026-08-27 refresh)

The consent-gated behavior is **already implemented** (single `IdentityApplicationUserPrincipal`,
JSON request DTO, consent-aware provisioning, audit). These tasks reconcile the existing code to the
refreshed design decided on 2026-08-27: **drop OIDC compatibility**, rename/retype identifiers to the
system's `UniqueId` type with self-explaining names (`userUniqueId`, `applicationUniqueId`,
`workspaceUniqueId`), and use `URI` for the application `uri`. See [research.md](research.md) Decision 7
and [data-model.md](data-model.md). Tasks are phrased as the delta (retype/rename/verify), not
greenfield creation.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Restore the dependency the retyped public records need.

- [X] T001 Re-add `api "vg.unique-id:unique-id-api:${vg_unique_id_version}"` to `identity-api/build.gradle` (the public records will reference `vg.unique.id.model.UniqueId`, which was dropped when the principal interfaces moved to `identity-logic`). Confirm `java.net.URI` is JDK-only (no dependency needed).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared contract types and cross-boundary JSON handling that BOTH US1 and US2 depend on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 [P] Retype the Telegram user principal in `identity-api/src/main/java/vg/identity/model/IdentityApplicationUserPrincipal.java`: rename `sub` (String) → `userUniqueId` (`UniqueId`, nullable), remove all OIDC/claim language from the Javadoc, keep the `PERMISSIONS_SCOPE` constant, keep `implements Serializable` (`UniqueId` is `Serializable`), and document the reserved `preferredLocale` placeholder (domain-native name, not yet a field). Update the `consentGiven == (userUniqueId != null)` invariant text.
- [X] T003 [P] Retype the application principal in `identity-api/src/main/java/vg/identity/model/IdentityApplicationPrincipal.java`: `sub` (String) → `applicationUniqueId` (`UniqueId`), `workspaceUniqueId` (Long) → `UniqueId`, `uri` (String) → `URI`; remove the `//TODO VG make OIDC compatible?` marker and OIDC references in the Javadoc.
- [X] T004 Verify `UniqueId` round-trips as a JSON string across the REST boundary with **no custom serializer** — it already carries `@JsonValue` on `toString()` (emits the encoded string form) and `@JsonCreator(mode = DELEGATING)` on `parse(String)`, so both the server (Spring MVC Jackson) and the `identity-rest-client` `RestClient` (Jackson message converters) handle it natively. Confirm `URI` uses Jackson's built-in string form too. This is a confirmation task (a serialization round-trip assertion), not new wiring. Depends on T002, T003.

**Checkpoint**: Contract types compile with `UniqueId`/`URI` and round-trip as strings over HTTP with no custom Jackson config.

---

## Phase 3: User Story 1 - Identify a user without storing anything (Priority: P1) 🎯 MVP

**Goal**: Valid Telegram data + no consent → 200 with `userUniqueId: null`, display name, empty permissions, `consentGiven: false`, and nothing persisted.

**Independent Test**: POST valid init data with `consentToKeepPersonalData: false`; assert `userUniqueId` null, `name` present, `permissions` empty, no user row created.

### Tests for User Story 1 ⚠️

- [X] T005 [P] [US1] Update the pre-consent unit assertions (`userUniqueId` null, permissions empty, `consentGiven` false) in `identity-logic/src/test/java/vg/identity/service/IdentityApplicationApiServiceTest.java`.
- [X] T006 [P] [US1] Update the controller pre-consent 200-body assertions to the new field name/shape in `identity-rest-server/src/test/java/vg/identity/rest/server/controller/IdentityApplicationControllerTest.java`.
- [X] T007 [P] [US1] Update the pre-consent end-to-end scenario (`userUniqueId` null, zero persistence) in `identity-integration-tests/src/test/java/vg/identity/rest/IdentityApplicationRestClientIntegrationTest.java`.

### Implementation for User Story 1

- [X] T008 [US1] Update `preConsentPrincipal(...)` to build the principal with the `userUniqueId` field left null (builder method rename) in `identity-logic/src/main/java/vg/identity/service/IdentityApplicationApiService.java`.
- [X] T021 [P] [US1] Add an edge-case test asserting invalid/unverifiable `telegramInitData` → **204 No Content**, empty body, and **zero** persistence, for both `consentToKeepPersonalData` true and false (neutral-failure, no leak of which input failed) in `identity-logic/src/test/java/vg/identity/service/IdentityApplicationApiServiceTest.java` and `identity-integration-tests/src/test/java/vg/identity/rest/IdentityApplicationRestClientIntegrationTest.java` (FR-013, SC-006; security behavior per Constitution V).

**Checkpoint**: US1 passes independently — the no-storage path returns the typed principal, and invalid data returns 204 with nothing persisted.

---

## Phase 4: User Story 2 - Establish the user once consent is granted (Priority: P1)

**Goal**: Valid Telegram data + consent → user created once, 200 with non-null `userUniqueId` (`UniqueId`) and the application-scoped permissions; re-submitting resolves the same id.

**Independent Test**: POST valid init data with `consentToKeepPersonalData: true`; assert exactly one user created, `userUniqueId` non-null, permissions match; re-submit → same `userUniqueId`, no duplicate.

### Tests for User Story 2 ⚠️

- [X] T009 [P] [US2] Update the established-principal unit assertions (non-null `userUniqueId` as `UniqueId`, permissions exact, `consentGiven` true, no duplicate on re-submit) in `identity-logic/src/test/java/vg/identity/service/IdentityApplicationApiServiceTest.java`.
- [X] T010 [P] [US2] Update the consent-path end-to-end scenario (create-once, same id on re-submit) in `identity-integration-tests/src/test/java/vg/identity/rest/IdentityApplicationRestClientIntegrationTest.java`.

### Implementation for User Story 2

- [X] T011 [US2] Update `establishedPrincipal(...)` to set `.userUniqueId(userUniqueId)` directly (drop `.sub(userUniqueId.toString())`), keeping the existing permission lookup, in `identity-logic/src/main/java/vg/identity/service/IdentityApplicationApiService.java`.

**Checkpoint**: US1 and US2 both pass; identifiers are typed `UniqueId` end-to-end.

---

## Phase 5: User Story 3 - Reserve a placeholder for additional data (Priority: P2)

**Goal**: The contract reserves an optional additional-data slot (preferred locale) with a domain-native name, absent in every response this feature produces.

**Independent Test**: Inspect the contract/response; the `preferredLocale` placeholder is defined but omitted in all resolutions.

### Tests for User Story 3 ⚠️

- [X] T012 [P] [US3] Assert the `preferredLocale` placeholder is absent (unpopulated) in both pre-consent and established responses in `identity-logic/src/test/java/vg/identity/service/IdentityApplicationApiServiceTest.java` (and the controller body test if it asserts full shape).

### Implementation for User Story 3

- [X] T013 [US3] Confirm the reserved `preferredLocale` placeholder is documented on the record (no production field added by this feature) in `identity-api/src/main/java/vg/identity/model/IdentityApplicationUserPrincipal.java` (verifies T002's Javadoc reservation).

**Checkpoint**: The placeholder is reserved with a domain-native name and never populated.

---

## Phase 6: User Story 4 - Extend the principal without breaking consumers (Priority: P3)

**Goal**: Adding a new optional field later does not force existing consumers to change.

**Independent Test**: A consumer reading only the currently defined fields keeps working when an unknown optional field appears in the payload.

### Tests for User Story 4 ⚠️

- [X] T014 [P] [US4] Add/verify a tolerant-deserialization test: the typed client ignores an unknown optional field in the principal JSON, in `identity-integration-tests/src/test/java/vg/identity/rest/IdentityApplicationRestClientIntegrationTest.java` (or a focused `identity-rest-client` test). Confirm the client `ObjectMapper` does not fail on unknown properties.

**Checkpoint**: Forward-compatibility of the principal contract is proven.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Retype the `me()` application-principal path and finish verification. (These touch the application principal, which is not tied to a single Telegram user story.)

- [X] T015 Retype both `me()` build sites to pass `UniqueId`/`URI`: pass `application.getUniqueId()` directly (drop `.toString()`), wrap `getWorkspaceUniqueId()` (Long) as `new UniqueId(...)`, and parse `getUri()` (String) to `URI` — in `identity-logic/src/main/java/vg/identity/service/IdentityApplicationApiService.java` (`me()`) and `identity-rest-server/src/main/java/vg/identity/rest/server/controller/IdentityApplicationController.java` (`me()`).
- [X] T016 Consolidate the duplicated `me()` mapping: have `IdentityApplicationController.me()` delegate to `applicationApi.me()` instead of re-building `IdentityApplicationPrincipal`, removing the drift risk between the two build sites (`identity-rest-server` controller).
- [X] T017 [P] Update `me()` assertions for the typed application principal (`applicationUniqueId`/`workspaceUniqueId` as `UniqueId` strings, `uri` as `URI`) in the controller test and in `identity-integration-tests/src/test/java/vg/identity/rest/IdentityApplicationRestClientIntegrationTest.java`.
- [X] T022 [US2] Update the shared embedded-vs-remote conformance suite for the retyped `me()` application principal (`applicationUniqueId`/`workspaceUniqueId` as `UniqueId`, `uri` as `URI`) so embedded ≡ remote still holds, in `identity-integration-tests/src/test/java/vg/identity/rest/EmbeddedVsRemoteConformanceTest.java` (Constitution VI). Must be green before T020.
- [X] T023 [US2] Extend the shared conformance suite to cover `authenticateTelegram` through **both** the embedded `IdentityApplicationApiService` and the remote `IdentityApplicationApiRestClient`, asserting equivalent results for the pre-consent (`userUniqueId` null) and established (`userUniqueId` present) states, in `identity-integration-tests/src/test/java/vg/identity/rest/EmbeddedVsRemoteConformanceTest.java` (Constitution VI: an operation is not complete until both implementations pass the shared suite).
- [X] T018 [P] Verify audit records reference the user by `userUniqueId` and contain no secrets/PII in `identity-logic/src/main/java/vg/identity/audit/TelegramAuthenticationAuditor.java` and its test.
- [X] T019 [P] Confirm `PERMISSIONS_SCOPE` consumers still compile (constant name unchanged): `identity-frontend-vaadin/.../admin/IdentityApplicationClaims.java` and claim tests (grep-only check).
- [X] T020 Run the quickstart validation and targeted suites: `./gradlew :identity-api:test :identity-logic:test :identity-rest-server:test :identity-rest-client:test :identity-integration-tests:test` (see [quickstart.md](quickstart.md)).

---

## Phase 8: Actualization (Constitution — required final phase)

**Purpose**: Update the authoritative current-state specification set so it reflects the shipped change. Per the constitution (Development Workflow), a change is **not complete** until this is done.

- [X] T024 Actualize `specs/current/`: add/update the domain/feature file(s) describing the consent-gated, **non-OIDC**, typed-identifier principal contract (`userUniqueId: UniqueId`, application principal `applicationUniqueId`/`workspaceUniqueId: UniqueId`, `uri: URI`, reserved `preferredLocale`), remove any superseded OIDC/`sub` content, and keep cross-references and any ordering prefixes consistent. Use self-explaining filenames. Confirm review checks this actualization was performed (Constitution §Development Workflow L221-225, §Specification Artifacts).

**Checkpoint**: `specs/current/` matches the implemented behavior; no superseded content remains.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies — start immediately.
- **Foundational (Phase 2)**: depends on Setup; **blocks** all user stories. T004 depends on T002+T003.
- **User Stories (Phase 3–6)**: depend on Foundational. US1 and US2 are both P1; US3/US4 build on the same contract.
- **Polish (Phase 7)**: T015/T016 depend on T003 (retyped record) + T004 (JSON module); verification tasks (T017–T020) depend on the code tasks they cover.
- **Actualization (Phase 8)**: runs last, after all implementation + verification tasks; T024 depends on T001–T023.
- T022/T023 (conformance suite) depend on T002/T003 (retyped records) and T015 (retyped `me()` build sites), and must pass before T020's green-build gate.

### User story dependencies

- **US1 (P1)**: after Foundational — independently testable (no-storage path).
- **US2 (P1)**: after Foundational — independently testable (consent path). Shares the record with US1 but has its own build site (`establishedPrincipal`).
- **US3 (P2)**: after T002 (placeholder reservation).
- **US4 (P3)**: after Foundational (tolerant deserialization on the client).

### Within each user story

- Update tests first (they should fail against the old field name/shape), then apply the implementation change, then re-run.

### Parallel opportunities

- Setup: T001 alone.
- Foundational: T002 and T003 in parallel; T004 after both.
- US1: T005, T006, T007 in parallel; then T008. T021 can run in parallel with T005–T007 (separate assertions/scenarios).
- US2: T009, T010 in parallel; then T011.
- Polish: T017, T018, T019 in parallel after their code tasks; T020 last. T022 and T023 touch the same conformance file — do them sequentially, not [P].

---

## Parallel Example: Foundational + US1

```bash
# After T001, retype both records in parallel:
Task: "T002 Retype IdentityApplicationUserPrincipal (sub → userUniqueId: UniqueId)"
Task: "T003 Retype IdentityApplicationPrincipal (UniqueId/URI fields)"
# Then wire JSON: T004. Then US1 tests in parallel:
Task: "T005 pre-consent unit assertions"
Task: "T006 controller pre-consent body assertions"
Task: "T007 integration pre-consent scenario"
```

---

## Implementation Strategy

### MVP first (US1)

1. Phase 1 Setup (T001).
2. Phase 2 Foundational (T002–T004) — **critical**, blocks everything.
3. Phase 3 US1 (T005–T008) → validate the no-storage path → this is the MVP slice.

### Incremental delivery

1. Setup + Foundational → contract types typed and round-tripping.
2. US1 → no-storage identify (MVP).
3. US2 → consent-establish path.
4. US3/US4 → placeholder reservation + forward-compatibility.
5. Polish → `me()` application-principal retyping, consolidation, and full verification.

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks.
- The record type names (`IdentityApplicationUserPrincipal`, `IdentityApplicationPrincipal`) are
  unchanged — only their **fields/types** change — so consumer imports stay valid; watch for build sites
  that call `.sub(...)` / pass `.toString()` / pass the `Long` workspace id.
- Per [TESTING.md](../../TESTING.md), prefer targeted Gradle runs while iterating and the broader suite
  when shared persistence/security/config is touched.
- `UniqueId` needs **no** custom Jackson config: it ships with `@JsonValue` (`toString()` → encoded
  string) and `@JsonCreator(DELEGATING)` (`parse(String)`), so it serializes/deserializes as a string
  on both server and client out of the box. T004 only confirms this with a round-trip assertion.
