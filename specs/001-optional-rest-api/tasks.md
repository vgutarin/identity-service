---
description: "Task list for Optional REST API on Vaadin App Startup"
---

# Tasks: Optional REST API on Vaadin App Startup

**Input**: Design documents from `/specs/001-optional-rest-api/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md),
[data-model.md](data-model.md), [contracts/](contracts/)

**Tests**: Included — the project constitution (Principle VII: Code Quality & Test Discipline)
mandates unit + functional/integration coverage, and Principle VI mandates an embedded-vs-remote
conformance suite.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Exact file paths are included in each description

## Conventions for this feature

- New library package base: `vg.identity.rest.server` (controllers, security, config, auto-config).
- New runner package base: `vg.identity.app`.
- Toggle property: `identity.rest.api.enabled` (boolean, default `false`).

---

## Phase 1: Setup (Module Scaffolding)

**Purpose**: Create the two new Gradle modules and register them in the build.

- [X] T001 [P] Create `identity-rest-server/build.gradle` mirroring `identity-logic` (plugins:
  `java-library`, `maven-publish`, `org.springframework.boot`, `io.spring.dependency-management`; the
  root `subprojects` block already applies the boot plugin and library consumption uses the `-plain`
  jar variant, so no special packaging config is needed). Dependencies: `implementation project(":identity-logic")`,
  `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`,
  `spring-boot-configuration-processor` (annotationProcessor), lombok; testImplementation
  `spring-boot-starter-test`, `assertj-core`, `vg.lib:test`, `project(":identity-rest-client")`, h2,
  `vg.unique-id:unique-id-logic`. (mapstruct omitted — not used by the moved code.)
- [X] T002 [P] Create `identity-rest-app/build.gradle` applying the `org.springframework.boot` plugin +
  `io.spring.dependency-management`; `implementation project(":identity-rest-server")` plus the web/data-jpa/security
  starters (needed to compile the main); `runtimeOnly` h2; `testRuntimeOnly` `vg.unique-id:unique-id-logic`; lombok.
- [X] T003 Update `settings.gradle`: add `include 'identity-rest-server'` and `include 'identity-rest-app'`;
  remove `include 'identity-rest-service'`.

**Checkpoint**: Both new modules resolve in `./gradlew projects` (still empty of moved sources).

---

## Phase 2: Foundational (Module Split + Toggle Infrastructure)

**Purpose**: Perform the `identity-rest-service` → `identity-rest-server` (lib) + `identity-rest-app`
(runner) split and build the conditional auto-config that powers the toggle.

**⚠️ CRITICAL**: No user story can be implemented until this phase is complete.

- [X] T004 Move the web sources into the new package under `identity-rest-server/src/main/java/`:
  `IdentityApplicationController` → `vg/identity/rest/server/controller/IdentityApplicationController.java`,
  `ApiKeyAuthenticationFilter` → `vg/identity/rest/server/security/ApiKeyAuthenticationFilter.java`,
  `RestSecurityConfiguration` → `vg/identity/rest/server/config/RestSecurityConfiguration.java`. Behavior
  unchanged. Dropped the fully-commented `IdentityUserController` (out of scope).
- [X] T005 Move the standalone main into `identity-rest-app/src/main/java/vg/identity/app/IdentityApplication.java`
  and `application.properties` (encryption + email props) to
  `identity-rest-app/src/main/resources/application.properties`; added `identity.rest.api.enabled=true`
  there so the standalone runner preserves today's always-on behavior.
- [X] T006 [P] Create `identity-rest-server/src/main/java/vg/identity/rest/server/IdentityRestApiProperties.java`
  as `@ConfigurationProperties(prefix = "identity.rest.api")` with a typed `boolean enabled` field. This
  typed binding is what makes an unparseable value fail startup (FR-009) — see [data-model.md](data-model.md).
- [X] T007 Create `identity-rest-server/src/main/java/vg/identity/rest/server/IdentityRestApiAutoConfig.java`
  as `@AutoConfiguration` with `@EnableConfigurationProperties(IdentityRestApiProperties.class)` (always-on
  binding → fail-fast) plus startup mode logging (T012). **CORRECTION vs. original plan**: it does NOT
  `@Import` the web beans. Because `identity-logic`'s `@ComponentScan("vg.identity")` already discovers the
  `vg.identity.rest.server.*` package, importing here too would double-register the controller (ambiguous
  mapping). Instead the web beans self-register via that scan, gated individually (see T009).
- [X] T008 Register the auto-config in
  `identity-rest-server/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  (single line: `vg.identity.rest.server.IdentityRestApiAutoConfig`).
- [X] T009 **CORRECTION vs. original plan**: gate the web beans at the CLASS level with
  `@ConditionalOnBooleanProperty("identity.rest.api.enabled")` on the controller, `ApiKeyAuthenticationFilter`,
  and `RestSecurityConfiguration`. This is required because `identity-logic`'s broad `@ComponentScan("vg.identity")`
  would otherwise register the controller unconditionally (exposing `/api/**` even when disabled — and
  *unsecured*, since the security chain is gated off). The class-level condition is the real toggle guard and
  makes narrowing host component-scan (former T018) unnecessary.
- [X] T010 Deleted the old `identity-rest-service/` module directory (sources moved in T004/T005; live tests in T013).
- [X] T011 Update `identity-integration-tests`: `build.gradle` now depends on `project(":identity-rest-app")`
  (transitively brings `identity-rest-server`) instead of `project(":identity-rest-service")`;
  `BaseIntegrationTest` now imports `vg.identity.app.IdentityApplication`; and `application-test.properties`
  sets `identity.rest.api.enabled=true` so the rest-client integration test can reach `/api`.
- [X] T012 Startup logging of the effective mode implemented in `IdentityRestApiAutoConfig#reportEffectiveMode()`
  (always-on): logs ENABLED or DISABLED based on the bound property (FR-006). No API key is logged.
- [X] T013 Moved the two live unit tests to `identity-rest-server/src/test/java/vg/identity/rest/server/`
  (`controller/IdentityApplicationControllerTest`, `security/ApiKeyAuthenticationFilterTest`) — both pass.
  **CORRECTION vs. original plan**: dropped the dead `BaseRestControllerTest` (a `@SpringBootTest` base with
  no live subclass — its only user was the fully-commented `UserControllerTest`) and `UserControllerTest`,
  rather than moving a broken `@SpringBootTest` into a library with no `@SpringBootApplication`. Test resource
  `application-test.properties` was not carried over (the moved tests are pure unit tests needing no context).

**Checkpoint**: ✅ DONE — `./gradlew :identity-rest-server:test` passes (5/5); full-project
`compileJava`/`compileTestJava` succeeds; the standalone runner is now `identity-rest-app` with the REST API
enabled by default. (`bootRun` not executed here — no running datastore in this environment.)

---

## Phase 3: User Story 1 - Run the app without the REST API (Priority: P1) 🎯 MVP

**Goal**: Start `identity-frontend-vaadin` with the REST API disabled (default). UI fully functional; no
`/api/**` endpoints exposed.

**Independent Test**: Boot the Vaadin app with no override; UI works; a request to
`/api/v1/applications/me` is not served by a REST controller; startup logs "REST API disabled".

### Tests for User Story 1

- [X] T014 [P] [US1] Disabled-mode test implemented as `identity-rest-server/src/test/java/vg/identity/rest/server/RestApiToggleTest.java`
  using `ApplicationContextRunner` + a `@ComponentScan` that mirrors `identity-logic`'s discovery of the
  `vg.identity.rest.server.*` package. Asserts that with `identity.rest.api.enabled` unset OR `false`, there
  is no `IdentityApplicationController`, `ApiKeyAuthenticationFilter`, or `SecurityFilterChain` bean.
  (FR-002, FR-005, SC-002) **PLACEMENT NOTE**: put in `identity-rest-server` (where the toggle lives and can
  be tested with no DB/Vaadin boot) rather than a fragile full Vaadin `@SpringBootTest`. Enabled-mode
  assertions belong to US2's full-context test (T020).
- [X] T015 [P] [US1] Disabled-mode reporting covered in the same test: asserts
  `IdentityRestApiProperties.isEnabled()==false` and the auto-config logs "REST API is DISABLED" at startup
  (visible in test output). (FR-006, SC-005)

### Implementation for User Story 1

- [X] T016 [US1] Added `implementation project(":identity-rest-server")` to
  `identity-frontend-vaadin/build.gradle` (verified on the compile classpath; auto-config present, disabled by default).
- [X] T017 [US1] Added `identity.rest.api.enabled=false` to
  `identity-frontend-vaadin/src/main/resources/application.properties` (explicit safe default; FR-005).
- [ ] ~~T018~~ [US1] **OBSOLETE — removed during T001–T013 implementation.** The toggle is now guarded by
  class-level `@ConditionalOnBooleanProperty` (T009), which holds regardless of who component-scans the
  package. No change to `FrontendApplication`'s `scanBasePackages` is required. (Retained here as a record;
  no work to do.)
- [~] T019 [US1] Quickstart Scenario 1 (UI works, `/api` absent): the "/api absent when disabled" half is
  proven by the automated `RestApiToggleTest`; a manual `bootRun` of the Vaadin UI was not executed here (no
  running datastore in this environment). Run `./gradlew :identity-frontend-vaadin:bootRun` locally to
  confirm the UI end-to-end.

**Checkpoint**: ✅ Vaadin app is wired to run UI-only by default; disabled-mode toggle proven by automated
tests and full-project compile. (Manual UI bootRun deferred to a local run with a datastore.)

---

## Phase 4: User Story 2 - Run the app with the REST API enabled (Priority: P1)

**Goal**: Start the same Vaadin build with `identity.rest.api.enabled=true`; UI works AND `/api/**` is
exposed, requiring a valid API key.

**Independent Test**: Boot the Vaadin app with the flag on; UI works; `/api/v1/applications/me` returns 401
without a key and 200 with a valid `X-VG-Identity-API-Key`.

### Tests for User Story 2

- [X] T020 [P] [US2] Enabled-mode API behavior covered in `identity-integration-tests`
  (`IdentityApplicationRestClientIntegrationTest`, boots `identity-rest-app` with `identity.rest.api.enabled=true`):
  existing test asserts `me()` returns the safe metadata with a valid key (200, SC-003); added
  `me_whenApiKeyHeaderIsMissing_returnsUnauthorized` asserting 401 without the header (SC-004). Controller
  presence when enabled is proven by these calls succeeding against the running context. (FR-003, FR-007)
  **PLACEMENT NOTE**: kept in the running integration suite rather than a fragile full Vaadin `@SpringBootTest`.
- [X] T021 [P] [US2] Chain precedence + UI safety: `RestSecurityConfigurationTest` asserts the API-key filter
  is NOT registered as a global servlet filter (so a co-hosted UI is never rejected for lacking the key). The
  keyless-`/api` → 401 (not a login redirect) behavior is asserted by T020's 401 test against the `/api/**`
  chain. (FR-007, A3 in [contracts/rest-api-key-auth.md](contracts/rest-api-key-auth.md))

### Implementation for User Story 2

- [X] T022 [US2] `/api/**` `SecurityFilterChain` confirmed at `@Order(1)`; the Vaadin chain in
  `identity-frontend-vaadin/.../config/SecurityConfiguration.java` now has explicit `@Order(2)` so `/api/**`
  is always matched by the API-key chain first. Added `FilterRegistrationBean` (disabled) in
  `RestSecurityConfiguration` so the API-key filter runs ONLY inside the `/api/**` chain, never as a global
  servlet filter — this is what keeps the Vaadin UI working when the API is enabled. (FR-003, FR-007)
- [X] T023 [US2] Enable mechanism + API-key header documented in `identity-rest-server/README.md`
  (env var `IDENTITY_REST_API_ENABLED=true` or `--identity.rest.api.enabled=true`; `X-VG-Identity-API-Key`
  usage; default-disabled; TLS expectation). (FR-001, FR-004)
- [X] T024 [US2] Quickstart Scenario 2: the API 401/200 behavior is **executed and passing** in the integration
  suite (`IdentityApplicationRestClientIntegrationTest`: valid key → 200, missing key → 401). A manual co-hosted
  Vaadin run (`--identity.rest.api.enabled=true`, eyeball UI + `/api` together) is the only remaining manual step.

**Checkpoint**: ✅ Code complete — the API-key filter is scoped to the `/api/**` chain (UI safe when enabled),
`/api/**` takes precedence over the Vaadin chain, and the enable switch is documented. Full end-to-end
validation (integration suite + manual Vaadin boot) requires a datastore/Docker.

---

## Phase 5: User Story 3 - Choose the mode at startup without code changes (Priority: P2)

**Goal**: One build, both modes selectable by configuration only; invalid values fail fast; embedded and
remote implementations stay equivalent.

**Independent Test**: Run the same artifact twice (flag off, then on) and observe UI-only vs UI+API; an
invalid flag value halts startup.

### Tests for User Story 3

- [X] T025 [P] [US3] `IdentityRestApiPropertiesBindingTest` (identity-rest-server): asserts a context with
  `identity.rest.api.enabled=maybe` fails to start (`startupFails_whenEnabledValueIsNotBoolean`), and that the
  value binds to `false` when absent and `true` when set. Passes here (no DB needed). (FR-009, SC-006)
- [X] T026 [P] [US3] `EmbeddedVsRemoteConformanceTest`
  (`identity-integration-tests/src/test/java/vg/identity/rest/EmbeddedVsRemoteConformanceTest.java`): invokes
  `me()` via the embedded `IdentityApplicationApiService` (running as the same application principal) and via
  `IdentityApplicationApiRestClient`, asserting equal `AuthenticatedIdentityApplication`. (Principle VI,
  FR-008, SC-003) **Executed and passing** against the MySQL Testcontainer. `authenticateTelegram` conformance
  is also exercised remotely by `IdentityApplicationRestClientIntegrationTest`.
- **[X] Fix**: the conformance test originally used `PreAuthenticatedAuthenticationToken` (spring-security-web,
  not on the integration-tests compile classpath); switched to `UsernamePasswordAuthenticationToken`
  (spring-security-core) — the embedded service only reads `authentication.getPrincipal()`.

### Implementation for User Story 3

- [X] T027 [US3] `IdentityRestApiProperties` is bound unconditionally via `@EnableConfigurationProperties` in
  the always-on `IdentityRestApiAutoConfig` (implemented in T007); the T025 tests confirm the binding runs
  (and fails fast) regardless of the enabled/disabled state.
- [~] T028 [US3] Quickstart Scenario 4 (invalid value fails fast) is proven by T025. Scenario 3 (same build,
  config-only switch) is inherent — proven jointly by `RestApiToggleTest` (disabled) and the enabled-mode
  integration tests. A manual two-run `bootRun` on one artifact is deferred to a local run with a datastore.

**Checkpoint**: ✅ Toggle is config-only and safe: fails fast on invalid input, binds safe-by-default, and the
embedded/remote implementations are asserted equivalent (execution pending Docker).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T029 [P] Operator documentation delivered in `identity-rest-server/README.md` (added in US2/T023):
  default-disabled, how to enable (env var / arg), the `X-VG-Identity-API-Key` contract, precedence, and TLS.
- [X] T030 [P] Security review: `ApiKeyAuthenticationFilter` logs nothing; `IdentityRestApiAutoConfig` logs only
  the ENABLED/DISABLED mode string. The API key value is never written to logs or error responses. (Principles III/V)
- [X] T031 Stale `identity-rest-service` references: **none in tracked source/build files** (the remaining refs
  under `specs/` are intentional design/history). Two stale doc refs exist in **untracked, pre-existing** files
  — `identity-logic/README.md` (module overview) and `prompts/2026-02-27-logic-api-key.md` — left for the owner
  to update, since they are outside this feature's committed set.
- [X] T032 Quickstart validation: Scenarios 1 (disabled), 3 (config-only), 4 (fail-fast) proven by
  `RestApiToggleTest` / `IdentityRestApiPropertiesBindingTest`; Scenario 2 (enabled 401/200) proven by the
  integration suite. Only the manual UI `bootRun` eyeball (Scenarios 1/2/5 visual) remains optional.
- [X] T033 `./gradlew test` is **green across all modules**, including the MySQL-Testcontainer integration
  suite (`identity-integration-tests`, `identity-logic`). A full `./gradlew build` (adds the Vaadin production
  frontend build) was not separately run.

---

## Phase 7: Security Audit Logging (G1 — constitution Principle V)

**Purpose**: Close the `/speckit-analyze` G1 finding — the enabled REST API must produce an auditable record
of authentication events. **Design decisions** (agreed): failures are logged **per-event** (durable,
forensic); successes are **counted** and flushed periodically (volume). Flush interval is configurable,
default 10 minutes. `remoteAddr` is kept only on per-event failure records, never in the counter key (bounded
cardinality). On a failure where a key value was supplied, the key **id** (UUID) is logged — never the secret.
All records go through a dedicated logger `vg.identity.security.audit`; genuine tamper-evidence is delivered
operationally by routing that logger to an append-only sink (documented, not enforced in-app).

**Depends on**: Phase 2 (rest-server + toggle) — the audit beans are gated with the same
`@ConditionalOnBooleanProperty("identity.rest.api.enabled")`. `@EnableScheduling` is already active via
`IdentityLogicConfig`.

- [X] T034 `IdentityRestApiProperties` now has a nested `Audit` with `flushInterval` (`Duration`, default
  `PT10M`) and `maxCounterKeys` (default `10000`); binding stays unconditional.
- [X] T035 Added `IdentityApiKeyService.extractKeyId(String): Optional<String>` in `identity-logic` — reuses the
  existing `parse(...)` and returns only the `id` (UUID), never the secret. (Added to the intentionally-unsecured
  allowlist in `IdentityApiKeyServicePermissionIntegrationTest`.)
- [X] T036 Created `ApiAuthenticationAuditor` (`identity-rest-server/.../audit/`, gated), logging via
  `vg.identity.security.audit`: per-event `failure(reason, method, path, remoteAddr, keyId)` and counted
  `success(applicationUniqueId, method, path)` (no `remoteAddr` in the key), with a `maxCounterKeys` cap folding
  excess into an `__overflow__` bucket (one-time warning). All request-derived fields sanitized (control chars →
  `_`, length-capped) as a log-injection guard; the key value is never logged.
- [X] T037 Scheduled flush `@Scheduled(fixedDelayString = "${identity.rest.api.audit.flush-interval:PT10M}")` —
  snapshot-and-reset per key, one aggregated line with count + window `start`/`end`; `@PreDestroy` final flush.
- [X] T038 Wired the auditor into `ApiKeyAuthenticationFilter`'s three outcomes; on `invalid_key` the key id comes
  from `extractKeyId` (raw value never forwarded). Behavior otherwise unchanged.
- [X] T039 [P] Tests: `ApiAuthenticationAuditorTest` (failure record + keyId, `keyId=none`, log-injection
  sanitization, success flush aggregation with window bounds, overflow bucket, and the **key-value-never-logged**
  assertion) + filter-level verifications that only the key id is forwarded. All pass.
- [X] T040 [P] Documented the audit logger, `flush-interval`/`max-counter-keys` properties, and the operational
  append-only-sink requirement in `identity-rest-server/README.md`.

**Checkpoint**: ✅ enabled API emits per-event failure audit records + periodic success counts via the dedicated
logger; no secret is ever logged; G1 resolved at the code level (tamper-evidence completed operationally).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 — **BLOCKS all user stories** (the split + auto-config must
  exist first).
- **User Stories (Phase 3–5)**: All depend on Phase 2.
  - US1 (P1) and US2 (P1) both touch `identity-frontend-vaadin`; US2 builds on the dependency/scan wiring
    added in US1 (T016/T018), so run US1 before US2.
  - US3 (P2) depends on the toggle existing (Phase 2) and is most meaningful after US1+US2.
- **Polish (Phase 6)**: After the desired user stories are complete.

### Critical-path notes

- T004/T005 (moves) must precede T007/T008 (auto-config referencing moved classes). ✅ done.
- **Class-level gating (T009) is the toggle guard**, not scan-boundary exclusion. Because `identity-logic`'s
  `@ComponentScan("vg.identity")` discovers the rest-server package regardless of host, the former T018
  (narrowing the Vaadin `scanBasePackages`) is NOT needed and should be dropped from US1. The disabled-mode
  test (T014) passes purely due to `@ConditionalOnBooleanProperty` on the classes.
- T007's unconditional properties binding is required for T025 (fail-fast test).

### Parallel Opportunities

- T001 and T002 (two new `build.gradle` files) run in parallel.
- Within Phase 2, T006 is `[P]` (new isolated file) alongside the moves.
- Test tasks within a story (T014/T015, T020/T021, T025/T026) are `[P]` — different files.
- Polish T029/T030 are `[P]`.

---

## Parallel Example: User Story 2

```bash
# Launch US2 tests together (different files):
Task: "Integration test RestApiEnabledTest (401 without key, 200 with key)"
Task: "Test API-key chain precedence (keyless /api -> 401, not login redirect)"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1: Setup (create modules).
2. Phase 2: Foundational (the split + conditional auto-config) — the bulk of the work.
3. Phase 3: US1 — Vaadin runs UI-only by default.
4. **STOP and VALIDATE**: UI works, `/api` absent, startup logs disabled. Demo the MVP.

### Incremental Delivery

1. Setup + Foundational → both modules build, standalone app unchanged.
2. US1 → Vaadin UI-only (safe default). **MVP.**
3. US2 → Vaadin + API-key-protected REST when enabled.
4. US3 → config-only switching + fail-fast + embedded/remote conformance.
5. Polish → docs, security review, full green build.

---

## Notes

- `[P]` = different files, no dependencies.
- The split (Phase 2) is a refactor with no behavior change for the standalone app; keep its tests green
  throughout as the regression guard.
- The toggle only works if the rest-server web beans are registered solely via the conditional auto-config
  (T007) and kept out of every host's component scan (T018) — treat these two as a pair.
- Commit after each task or logical group; keep the standalone app buildable at every checkpoint.
