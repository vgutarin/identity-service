# Feature Specification: Consent-Gated Principal From Telegram Data

**Feature Branch**: `003-telegram-principal-details`

**Created**: 2026-08-21

**Input**: User description: "API should provide principal object with displayName, uniqueId, permissions, optionally some additional data like preferred locale by given telegramData"

**Correction**: "Principal should distinguish whether consent to keep personal data is given. If consent is not given, uniqueId is null and the identity service stores no user data. Once consent is given, the identity service creates the user and returns a principal with a uniqueId."

**Clarification (superseded on 2026-08-27)**: An earlier clarification required the returned object to
be OIDC-compatible (fully once consent is given, only partially before). This requirement has been
**dropped** — see the 2026-08-27 Clarifications session. The principal is no longer required to be
OIDC-compatible and uses domain-native, self-explaining identifiers instead of OIDC standard claims.

**Clarification**: "No actual manipulation of additional data (such as preferred locale) is expected. The returned object only needs to reserve a placeholder for it — no derivation, population, or persistence of that additional data is to be implemented now."

## Clarifications

### Session 2026-08-23

- Q: Should the new consent-gated behavior replace the existing Telegram authentication endpoint's
  behavior, or be added as a separate capability? → A: Replace it in place — the existing Telegram
  authentication operation becomes consent-gated (a breaking change to current callers). The current
  REST path provisions and persists a user with no consent check, which is a consent-before-storage
  violation being corrected.
- Q: What should the returned object be called, given it spans the pre-consent (no unique identifier)
  and established (unique identifier present) states? → A: A single "principal" object with a consent
  indicator and a nullable unique identifier — one stable contract type across both states (not two
  distinct types).
- Q: When consent is granted and the user is created, should that consent+creation event be audited
  (in addition to authentication)? → A: Yes — record an audit event for both the consent-triggered
  user creation and the authentication; audit entries carry no raw Telegram personal data or secrets.

### Session 2026-08-27

- Q: Must the returned principals (the application principal and the Telegram user principal) be
  OIDC-compatible? → A: No. OIDC compatibility (claim-name/shape and protocol) is dropped for both.
  Identity fields use domain-native, self-explaining names rather than OIDC standard claim names such
  as `sub`, `name`, or `locale`.
- Q: How are identifiers represented on the principals? → A: As the system's unique-identifier type
  (UniqueId) with self-explaining field names (for example `userUniqueId`, `applicationUniqueId`,
  `workspaceUniqueId`), never raw strings or numbers.
- Q: How is the application's location/URL represented on the application principal? → A: As a URI
  value type, not a raw string.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Identify a user from Telegram data without storing anything (Priority: P1)

As a consumer application integrating with the identity service, I submit the Telegram sign-in data
I received from a user who has **not** granted consent to keep personal data, and I get back a
principal I can use to greet the user — a display name — while the identity service persists **no**
user record. In this state the principal's unique identifier is explicitly absent (null), signalling
to me that the user is not yet established and that consent is still required before any data is kept.

**Why this priority**: The service is not permitted to store user data before consent, so the
privacy-preserving, no-storage path is the non-negotiable default and the smallest viable slice: it
lets a consumer recognize and welcome a user, and clearly detect that consent is pending, without the
service retaining anything. This is the MVP.

**Independent Test**: Submit valid Telegram data for a user who has not granted consent; confirm the
principal is returned with a display name and a **null** unique identifier, that the response signals
consent is not yet given, and that no user record is created or persisted by the service.

**Acceptance Scenarios**:

1. **Given** valid Telegram data for a user who has not granted consent, **When** the consumer
   requests the principal, **Then** the response includes a display name, indicates consent is not
   given, and returns a null unique identifier.
2. **Given** the same request, **When** it completes, **Then** the identity service has created and
   persisted no user record and no personal data derived from that Telegram data.
3. **Given** consent has not been given, **When** the principal is resolved, **Then** it carries no
   application-scoped permissions (an empty set), because no established user exists to hold them.
4. **Given** the Telegram profile provides a first and/or last name, **When** the principal is
   resolved without consent, **Then** the display name is still a readable representation of that
   name (identification does not require storage).

---

### User Story 2 - Establish the user once consent is granted (Priority: P1)

As a consumer application, once the user grants consent to keep their personal data, I submit the
Telegram sign-in data together with that consent and the identity service creates the user and
returns a principal that now carries a stable unique identifier — so from that point on I can
recognize the same user across sessions and enforce their permissions.

**Why this priority**: Establishing the user upon consent is the other half of the consent gate and
the point at which the principal becomes fully usable (identifiable and authorizable). Without it the
feature can never move a user out of the provisional, no-identifier state, so it is also P1.

**Independent Test**: Submit valid Telegram data with consent granted for a not-yet-established user;
confirm the service creates the user, and the returned principal carries a non-null stable unique
identifier and the user's application-scoped permissions. Re-submitting later resolves the same
identifier.

**Acceptance Scenarios**:

1. **Given** valid Telegram data and consent granted for a user not yet established, **When** the
   consumer requests the principal, **Then** the identity service creates the user and returns a
   principal with a non-null stable unique identifier.
2. **Given** an already-established user (consent previously granted), **When** the principal is
   resolved from their Telegram data, **Then** the same unique identifier is returned without
   creating a duplicate user.
3. **Given** an established user with granted permissions in the calling application, **When** the
   principal is resolved, **Then** the returned permissions exactly match the permissions the user
   holds in that application's scope.
4. **Given** an established user with no permissions in the calling application, **When** the
   principal is resolved, **Then** the principal is returned with a non-null identifier and an empty
   permission set.

---

### User Story 3 - Reserve a placeholder for additional data such as preferred locale (Priority: P2)

As a maintainer of the identity service and its consumers, I want the principal contract to reserve a
placeholder slot for optional additional data such as preferred locale, so that a future feature can
populate it without changing the contract — but with **no** derivation, population, or persistence of
that data implemented now.

**Why this priority**: Reserving the slot up front keeps the contract stable for a later,
on-demand localization feature, but delivering actual locale data is explicitly not part of this
feature, so it sits below the P1 identity/consent behavior.

**Independent Test**: Inspect the principal contract and confirm the optional additional-data
placeholder (e.g. preferred locale) exists with a domain-native, self-explaining name; confirm that in
every resolution today the placeholder is absent (unpopulated), and that no derivation or persistence
of it occurs.

**Acceptance Scenarios**:

1. **Given** the principal contract, **When** a consumer inspects it, **Then** an optional
   additional-data placeholder (such as preferred locale) is defined and reserved.
2. **Given** any resolution performed by this feature, **When** the principal is returned, **Then**
   the additional-data placeholder is absent (this feature performs no derivation or population of it).
3. **Given** the placeholder is defined, **When** a consumer inspects it, **Then** it has a
   domain-native, self-explaining name and a well-defined value type, so a later feature can populate
   it without a contract change.
4. **Given** any resolution, **When** it completes, **Then** no additional data (such as locale) is
   persisted, in either the pre-consent or established state.

---

### User Story 4 - Extend the principal without breaking consumers (Priority: P3)

As a maintainer of the identity service and its consumers, I want additional optional profile data to
be addable to the principal over time so that new consumer needs can be met without forcing existing
consumers to change how they read the principal.

**Why this priority**: A forward-looking robustness property; it protects the P1/P2 contract from
becoming a source of breaking changes.

**Independent Test**: With an existing consumer that reads only the consent indicator, unique
identifier, display name, and permissions, introduce a new optional field; confirm the existing
consumer continues to work unchanged and ignores the new field.

**Acceptance Scenarios**:

1. **Given** an existing consumer reading only the currently defined fields, **When** a new optional
   field is added to the principal, **Then** the existing consumer continues to function with no
   changes.
2. **Given** an optional field has no value for a particular user, **When** the principal is
   resolved, **Then** the field is omitted rather than returned with a placeholder value.

---

### Edge Cases

- **Consent not given**: the unique identifier is null, permissions are empty, and the service
  persists nothing — the pre-consent principal is purely a transient view derived from the supplied
  data.
- **Consent indicator vs. identifier consistency**: a principal indicating "consent not given" always
  has a null identifier, and a principal with a non-null identifier always indicates consent is given;
  the two never disagree.
- **Consent granted for a user who already exists**: no duplicate user is created; the existing
  identifier is returned.
- **Invalid or unverifiable Telegram data**: no principal is resolved (neither pre-consent nor
  established), and nothing is persisted; the response does not reveal which input was rejected.
- **Display name not derivable from name fields**: a defined fallback is used (e.g. the Telegram
  username) so the display name is never blank when any identifying value exists — in both states.
- **Additional data (e.g. preferred locale)**: the placeholder is reserved but unpopulated by this
  feature, so it is always omitted for now; no derivation, defaulting, or persistence of it occurs.
- **Caller is not an authenticated/authorized application**: the request is rejected before any
  resolution or provisioning occurs.
- **Consent is submitted but the underlying data is invalid**: no user is created (consent alone does
  not cause storage of unverifiable data).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The API MUST accept Telegram sign-in data from an authenticated, authorized consumer
  application, together with an indication of whether the user has granted consent to keep personal
  data, and return a single principal object describing the user.
- **FR-002**: The principal MUST explicitly indicate whether consent to keep personal data has been
  given.
- **FR-003**: When consent has NOT been given, the identity service MUST NOT create or persist any
  user record or personal data derived from the supplied Telegram data.
- **FR-004**: When consent has NOT been given, the principal's unique identifier MUST be null.
- **FR-005**: When consent has been given and the resolved user is not yet established, the identity
  service MUST create the user and return a principal whose unique identifier is non-null and stable.
- **FR-006**: When consent has been given for a user who is already established, the service MUST NOT
  create a duplicate and MUST return the existing stable unique identifier.
- **FR-007**: A principal indicating "consent not given" MUST always have a null unique identifier,
  and a principal with a non-null unique identifier MUST always indicate consent has been given (the
  consent indicator and the identifier are never contradictory).
- **FR-008**: The principal MUST include a human-readable display name derived from the Telegram
  profile in both states, using a defined fallback when name fields are absent so the display name is
  never blank when any identifying value is available.
- **FR-009**: The principal MUST include the set of permissions the user holds within the calling
  application's authorization scope, determined server-side; when no established user exists (consent
  not given) this set MUST be empty.
- **FR-010**: The principal contract MUST reserve an OPTIONAL placeholder for additional data such as
  preferred locale. This feature MUST NOT derive, populate, or persist that additional data; the
  placeholder MUST be absent in every resolution this feature produces.
- **FR-011**: A reserved additional-data placeholder (e.g. preferred locale) MUST be defined with a
  domain-native, self-explaining name and a well-defined value type, so a future feature can populate
  it without a contract change.
- **FR-012**: Optional principal fields (such as preferred locale and any future additions) MUST be
  omitted when they have no value, and their absence MUST NOT be treated as an error.
- **FR-013**: When the Telegram data is invalid, unverifiable, or does not resolve to a user, the API
  MUST NOT return a principal and MUST NOT persist anything, regardless of the consent indication.
- **FR-014**: The principal MUST NOT contain secrets, credentials, or tokens, nor personal data
  beyond what is required to identify, greet, and authorize the user.
- **FR-015**: Permissions in the principal MUST reflect only the calling application's scope and MUST
  NOT leak permissions or claims from other applications or workspaces.
- **FR-016**: The principal contract MUST be extensible so that adding a new optional field does not
  require changes to existing consumers that read only previously defined fields.
- **FR-017**: The returned principals MUST NOT be required to be OIDC-compatible. Their identity fields
  MUST use domain-native, self-explaining names (e.g. `userUniqueId`, `applicationUniqueId`) rather
  than OIDC standard claim names (`sub`, `name`, `locale`), and the contract MUST NOT commit to OIDC
  claim shape or protocol.
- **FR-018**: Every unique identifier carried by a principal MUST be represented using the system's
  unique-identifier type (UniqueId), never a raw string or number, and MUST be named to state what it
  identifies (e.g. `userUniqueId` for the resolved user, `applicationUniqueId` for the calling
  application, `workspaceUniqueId` for the workspace).
- **FR-019**: The application principal's location/reference MUST be represented as a URI value type,
  not a raw string, so consumers receive an already-validated, well-formed address.
- **FR-020**: Unique identifiers exposed on a principal MUST be stable and MUST NOT be reassigned or
  recycled across the lifetime of the entity they identify, so a consumer can rely on them to recognize
  the same user or application over time.
- **FR-021**: The consent-gated behavior MUST replace the existing Telegram authentication operation's
  behavior in place. The existing operation currently provisions and persists an identity user with no
  consent check; after this change it MUST NOT create or persist a user until consent is given
  (FR-003). This is an accepted breaking change to current callers of that operation.
- **FR-022**: When consent is granted and a user is created, the service MUST record an audit event
  for the consent-triggered user creation, and MUST record the authentication event, in a structured,
  tamper-evident form.
- **FR-023**: Audit entries MUST NOT contain secrets, tokens, or raw Telegram personal data (e.g. the
  raw sign-in payload or profile fields); they reference the user by stable identifier only.

### Key Entities *(include if feature involves data)*

- **Principal (from Telegram data)**: The consumer-facing result of resolving supplied Telegram
  sign-in data for one application scope. It exists in two states distinguished by a consent
  indicator:
  - *Pre-consent (provisional)*: display name (required), user unique identifier **null/absent**,
    permissions empty, consent indicator = "not given", additional-data placeholder (e.g. preferred
    locale) reserved but unpopulated. Backed by **no** stored record.
  - *Established*: non-null stable user unique identifier, display name, application-scoped permissions
    (possibly empty), consent indicator = "given", additional-data placeholder (e.g. preferred locale)
    reserved but unpopulated. Backed by a persisted user created at the moment consent was granted.
  The identity fields use domain-native, self-explaining names; the user unique identifier is the
  system's unique-identifier type (UniqueId), never a raw string. The principal is **not** required to
  be OIDC-compatible — it does not use OIDC standard claim names or commit to OIDC claim shape.
  Application-scoped permissions and the consent indicator are ordinary fields on the object.
  Naming note (decided, see Clarifications): a single object named "principal", carrying a consent
  indicator plus a nullable unique identifier, is used across both states — not two differently named
  objects.
- **Application principal**: The safe metadata describing the calling application (returned when the
  application inspects itself). Its identifiers — the application unique identifier and the workspace
  unique identifier — are the system's unique-identifier type (UniqueId) with self-explaining names,
  and its location/reference is a URI value type. Like the Telegram user principal, it is not required
  to be OIDC-compatible.
- **Consent to keep personal data**: The user's permission for the identity service to store their
  personal data. It gates user creation: no consent → no storage and no identifier; consent →
  user created and identifier issued.
- **Telegram data**: The sign-in payload the consumer obtained from the user's Telegram client and
  passes to the API. The source from which the user is verified and the display name is derived.
  Treated as untrusted input until verified, and never persisted before consent.
- **Additional-data placeholder**: A reserved optional slot on the principal (e.g. preferred locale)
  intended for a future feature. Defined for contract stability with a domain-native, self-explaining
  name, but not derived, populated, or persisted by this feature.
- **Permission**: A single capability a user holds within a specific application's authorization
  scope; the established principal carries the flattened set of these.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When consent is not given, 100% of resolutions return a null unique identifier and
  result in 0 persisted user records or personal-data rows attributable to that Telegram data.
- **SC-002**: When consent is given for a not-yet-established user, 100% of successful resolutions
  create exactly one user and return a non-null identifier; re-submitting the same user creates 0
  additional users.
- **SC-003**: The consent indicator and the unique identifier are consistent in 100% of returned
  principals (no "consent not given" principal has an identifier, and no principal with an identifier
  says consent is not given).
- **SC-004**: A consumer can greet a pre-consent user (display name) using only the single principal
  response, with 0 additional calls and 0 stored data.
- **SC-005**: The additional-data placeholder (e.g. preferred locale) is unpopulated in 100% of
  resolutions this feature produces, and 0 additional data (such as locale) is persisted.
- **SC-006**: Invalid, unverifiable, or non-resolvable Telegram data yields no principal and 0
  persisted records in 100% of cases, regardless of the consent indication.
- **SC-007**: Introducing a new optional principal field requires 0 changes to existing consumers that
  read only previously defined fields.
- **SC-008**: The principal exposes 0 fields containing secrets, credentials, or tokens, and returned
  permissions show 0 cross-application or cross-workspace leakage.
- **SC-009**: 100% of principals represent every unique identifier as the system's unique-identifier
  type (UniqueId) with a self-explaining name (0 identifiers exposed as raw strings or numbers), the
  application principal's location is a valid URI in 100% of cases, and 0 principals use OIDC standard
  claim names.
- **SC-010**: 100% of consent-triggered user creations produce an audit record (plus the
  authentication record), and 0 audit entries contain secrets, tokens, or raw Telegram personal data.

## Out of Scope / Deferred

- **OIDC compatibility is out of scope** for these principals — both claim-shape compatibility (using
  OIDC standard claim names/value formats) and full protocol support. The principals use domain-native
  identifiers instead (FR-017). Should an OIDC-conformant surface be needed later, it would be a
  separate, additive feature that maps these domain fields onto OIDC claims at its own boundary.
- Explicitly out of scope now: OIDC protocol endpoints (discovery / `.well-known`, authorization,
  token, UserInfo), signed ID tokens and the signing-key infrastructure (JWKS, key rotation), the
  redirect-based authorization-code flow (PKCE, nonce/state), and token lifecycle (expiry, refresh,
  revocation).
- **Populating additional data (e.g. preferred locale) is deferred.** This feature only reserves the
  placeholder in the contract (FR-010, FR-011); deriving, populating, and persisting locale (or any
  other additional data) is a future feature.
- **Why a later OIDC surface stays cheap**: the identifier the contract locks now is stable and never
  reassigned or recycled (FR-020), which is the one property that is expensive to retrofit. A future
  OIDC layer can map the stable user unique identifier onto `sub` and the display name onto `name`
  without disturbing existing consumers of the domain-native contract.

## Assumptions

- This feature is scoped to resolving a principal from **Telegram sign-in data**; other authentication
  inputs are out of scope, though the extensible, consent-gated contract is expected to be reusable by
  them later.
- The consumer is an already-registered application authenticating with its existing application
  credentials; establishing that application authentication is out of scope and reused from the
  existing REST API.
- The **model chosen** for the returned object is a single principal with (a) an explicit consent
  indicator and (b) a nullable unique identifier, rather than two separate object types (decided in
  Clarifications). This matches the correction ("uniqueId is null when consent is not given") and
  keeps the contract stable as a user transitions from provisional to established.
- Consent to keep personal data is conveyed by the consumer on the resolution request (the same
  personal-data consent concept the service already uses); how the consumer collects that consent in
  its own UI is out of scope.
- The unique identifier returned for an established user is the user's existing identity; this
  feature exposes it in a typed, self-explaining form rather than introducing a new identifier.
- "Permissions" means the user's flattened, application-scoped permission strings the service already
  computes for authorization; this feature surfaces them on the established principal rather than
  redefining how they are computed.
- Additional data such as preferred locale is treated as a reserved placeholder only: this feature
  does not derive it from the Telegram data, populate it, or persist it. Actually filling the
  placeholder (from the Telegram-declared language or any stored preference) is a future feature.
- Display name is derived from the Telegram profile's name fields, falling back to the Telegram
  username when no first/last name is present.
- "Not permitted to store user data until consent is given" is treated as covering the persisted user
  record and personal data derived from the Telegram profile; transient, in-request processing needed
  to verify the data and build the pre-consent principal is not considered "storing".
- OIDC compatibility is **not** a requirement for these principals (decided 2026-08-27). The
  principals expose domain-native, self-explaining identifiers rather than OIDC standard claims; no
  commitment is made to OIDC claim shape or protocol.
- Identifiers on the principals are the system's existing unique-identifier type (UniqueId). This
  feature surfaces that existing identifier in a typed, self-explaining form (e.g. `userUniqueId`),
  rather than introducing a new identifier or exposing it as a raw string.
- The user unique identifier is necessarily absent before consent (no user exists) and non-null once
  consent establishes the user; its type and name are unchanged across both states — only its presence
  differs.
