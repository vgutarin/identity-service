# Feature Specification: Optional REST API on Vaadin App Startup

**Feature Branch**: `001-optional-rest-api`

**Created**: 2026-08-19

**Status**: Draft

**Input**: User description: "I want be able to start vaadin app with or without REST API enabled."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run the app without the REST API (Priority: P1)

As an operator, I want to start the Vaadin application with the REST API turned off so that the
deployment serves only the interactive UI and exposes no REST endpoints, reducing the externally
reachable surface.

**Why this priority**: This is the safety-first default posture and the simplest viable
deployment. Delivering only this mode already gives a fully usable UI-only application, so it is
the MVP.

**Independent Test**: Start the application in "REST API off" mode; confirm the UI is fully
usable and that no REST endpoints respond (requests to REST paths are rejected/unavailable).

**Acceptance Scenarios**:

1. **Given** the application is configured with the REST API disabled, **When** an operator starts
   it, **Then** the Vaadin UI loads and all UI-driven identity operations work normally.
2. **Given** the application started with the REST API disabled, **When** a client sends a request
   to a REST API path, **Then** the request is not served by a REST endpoint (no REST endpoint is
   exposed for that path).
3. **Given** the application started with the REST API disabled, **When** the operator inspects
   startup output/health information, **Then** it clearly indicates the REST API is disabled.

---

### User Story 2 - Run the app with the REST API enabled (Priority: P1)

As an operator, I want to start the same Vaadin application with the REST API turned on so that
out-of-process clients can call the identity operations over REST while the UI remains available.

**Why this priority**: The ability to expose the REST API is the other half of the requested
toggle and is required for out-of-process consumers. Without it the feature is incomplete, so it
is also P1.

**Independent Test**: Start the application in "REST API on" mode; confirm the UI is fully usable
and that REST endpoints respond to valid, authenticated/authorized requests.

**Acceptance Scenarios**:

1. **Given** the application is configured with the REST API enabled, **When** an operator starts
   it, **Then** both the Vaadin UI and the REST endpoints are available.
2. **Given** the application started with the REST API enabled, **When** an authorized client
   sends a valid REST request, **Then** it receives the expected result equivalent to performing
   the same operation through the UI.
3. **Given** the application started with the REST API enabled, **When** an unauthenticated or
   unauthorized client sends a REST request, **Then** the request is denied (authentication and
   authorization are enforced regardless of the toggle).
4. **Given** the application started with the REST API enabled, **When** the operator inspects
   startup output/health information, **Then** it clearly indicates the REST API is enabled.

---

### User Story 3 - Choose the mode at startup without code changes (Priority: P2)

As an operator, I want to select whether the REST API is enabled purely through startup
configuration, so I can run the same build in either mode across environments without rebuilding
or editing code.

**Why this priority**: Improves operability and matches the intent of "start with or without",
but the two core modes (US1, US2) already deliver the essential value, so this is P2.

**Independent Test**: Using one and the same application build, start it twice with only the
configuration changed; confirm the REST API is off in one run and on in the other.

**Acceptance Scenarios**:

1. **Given** a single application build, **When** the operator sets the REST-API configuration to
   "off" and starts it, **Then** the app runs in UI-only mode.
2. **Given** the same build, **When** the operator sets the configuration to "on" and starts it,
   **Then** the app runs with the REST API exposed.
3. **Given** no explicit REST-API configuration is provided, **When** the operator starts the app,
   **Then** it starts in the safe default mode (REST API disabled) and reports that state.

---

### Edge Cases

- What happens when the REST-API toggle is set to an invalid/unrecognized value? The application
  MUST fail fast at startup with a clear error rather than starting in an ambiguous state.
- How does the system behave for UI operations when the REST API is disabled? UI functionality
  MUST be unaffected — the toggle governs only the externally exposed REST surface, not the UI's
  ability to perform operations.
- What happens to authentication/authorization when the REST API is enabled? Access controls MUST
  apply to REST requests exactly as they do to UI-initiated operations; enabling the API MUST NOT
  create an unauthenticated path to data.
- What happens to shared behavior between UI-driven operations and REST operations? An operation
  invoked via REST MUST produce results equivalent to the same operation performed through the UI.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The application MUST support starting in two modes selectable at startup: "REST API
  enabled" and "REST API disabled".
- **FR-002**: When started with the REST API disabled, the application MUST NOT expose any REST
  endpoint, while the Vaadin UI remains fully functional.
- **FR-003**: When started with the REST API enabled, the application MUST expose the REST
  endpoints in addition to the fully functional Vaadin UI.
- **FR-004**: The choice of mode MUST be controllable through startup configuration for a single
  application build, requiring no code changes or rebuild to switch modes.
- **FR-005**: When no mode is explicitly configured, the application MUST default to the REST API
  being disabled (safe-by-default) and MUST clearly report the effective mode.
- **FR-006**: The application MUST clearly indicate the effective mode (REST API enabled/disabled)
  in its startup output and/or health/status information.
- **FR-007**: When the REST API is enabled, every REST request MUST be subject to the same
  authentication and authorization checks as the equivalent UI-driven operation; enabling the API
  MUST NOT bypass or weaken access control.
- **FR-008**: A REST operation and its equivalent UI-driven operation MUST produce equivalent
  observable results, so behavior does not diverge between the two entry points.
- **FR-009**: If the mode configuration value is invalid or unrecognized, the application MUST fail
  to start with a clear, actionable error rather than starting in an undefined state.
- **FR-010**: Disabling the REST API MUST NOT disable, degrade, or alter any UI capability.

### Key Entities *(include if feature involves data)*

- **Application Runtime Mode**: The effective startup posture of the application, with the
  attribute "REST API exposure" holding one of two values — enabled or disabled. It is determined
  at startup from configuration and is reported through startup/health information.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The same application build can be started in either mode using only configuration,
  with 100% of UI capabilities available in both modes.
- **SC-002**: When started in "REST API disabled" mode, 100% of requests to REST API paths are not
  served by a REST endpoint (0 REST endpoints reachable).
- **SC-003**: When started in "REST API enabled" mode, authorized REST requests succeed and return
  results equivalent to the corresponding UI operation in 100% of verified operations.
- **SC-004**: When started in "REST API enabled" mode, 100% of unauthenticated or unauthorized
  REST requests are denied.
- **SC-005**: An operator can determine the effective mode from startup/health information within
  the first check, with no ambiguity, in 100% of starts.
- **SC-006**: Starting with an invalid mode value results in a failed startup with a clear error in
  100% of cases (no silent or ambiguous startup).

## Assumptions

- The default mode is "REST API disabled", reflecting the project's safe-by-default and
  data-minimization principles; operators explicitly opt in to expose the REST API.
- The mode is selected at process startup and is fixed for the lifetime of that process; changing
  the mode at runtime without a restart is out of scope for this feature.
- The REST API and the UI operate over the same underlying identity operations, so both entry
  points share the same business behavior and access-control rules (consistent with the project's
  requirement that each API surface has both an embedded/in-process and a remote/REST-client
  path).
- Authentication and authorization mechanisms already exist and are reused; this feature governs
  whether the REST surface is exposed, not how identities are authenticated.
- Selecting the mode is an operator/deployment concern; end users of the UI are unaffected by and
  unaware of the toggle.
