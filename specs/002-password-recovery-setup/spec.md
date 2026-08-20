# Feature Specification: Password Recovery & Initial Setup

**Feature Branch**: `002-password-recovery-setup`

**Created**: 2026-08-19

**Status**: Draft

**Input**: User description: "recover or initial password setup logic"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Recover a forgotten password (Priority: P1)

A registered user who cannot sign in because they have forgotten their password
asks the service to help them regain access. They provide the email address tied
to their account, receive a message containing a secure, time-limited link, open
it, choose a new password that meets the password policy, and are then able to
sign in with that new password.

**Why this priority**: Account lockout from a forgotten password is the single
most common reason a user loses access to an identity service. Without a
self-service recovery path, every such user needs manual intervention. This
story is the minimum viable slice — it independently restores access for the
largest group of affected users.

**Independent Test**: Create a user with a known password, "forget" it, request
recovery for that user's email, follow the emailed link, set a new compliant
password, and confirm sign-in succeeds with the new password and fails with the
old one.

**Acceptance Scenarios**:

1. **Given** a user account exists with a verified email, **When** the user
   requests password recovery for that email, **Then** the system sends a
   message containing a single-use, time-limited link and shows a neutral
   confirmation that does not reveal whether the email is registered.
2. **Given** a valid, unexpired recovery link, **When** the user opens it and
   submits a new password that satisfies the password policy, **Then** the
   password is updated, the link can no longer be reused, and the user can sign
   in with the new password.
3. **Given** a valid recovery link, **When** the user submits a new password
   that violates the password policy, **Then** the system rejects it with a
   localized explanation and the link remains usable for another attempt.
4. **Given** a user requested recovery, **When** the user tries to sign in with
   their old password before completing the flow, **Then** the old password
   still works until a new password is actually set.

---

### User Story 2 - Set an initial password (Priority: P2)

An invited user, reached through a pending workspace email channel (an
invitation not yet claimed), needs to set their first password so they can sign
in with credentials. Their entry point is the **existing email-verification link**
sent for that invitation: opening it takes them to the verification screen where
they choose a password that meets the policy, claiming the invitation and gaining
a working credential.

**Why this priority**: Invited users with a pending channel cannot access the
product through credential sign-in until they set a password. This unblocks the
invitation path, but it serves a narrower group than forgotten-password
recovery, so it follows P1. There is **no separate setup-link request surface**
for invitees — initial setup rides on the email-verification link rather than the
recovery request flow.

**Independent Test**: Create a pending workspace email-channel invitation with no
associated password, open the invitation's email-verification link, set a
compliant password on the verification screen, and confirm the invited user can
now sign in with it.

**Acceptance Scenarios**:

1. **Given** a pending invitation channel with no usable password, **When** the
   invited user opens the invitation's email-verification link, **Then** they are
   guided to create a first password with the same policy and confirmation used
   elsewhere in the product.
2. **Given** the initial password has been set via the verification link, **When**
   the user signs in with it, **Then** sign-in succeeds, the invitation channel is
   claimed, and the verification link can no longer be reused.

---

### User Story 3 - Request a fresh link when the previous one is unusable (Priority: P3)

A user whose **recovery** link has expired, was already used, or never arrived
requests a new one from the recovery request surface and completes the flow with
the fresh link. (Renewing an expired *initial-setup* entry is handled by the
existing email-verification resend, not this request surface — see FR-001b.)

**Why this priority**: Links are intentionally short-lived and single-use, so a
non-trivial share of users will need a second link. It is a refinement on the P1
happy path rather than a standalone value driver.

**Independent Test**: Let a recovery link expire (or use it once), request a new
link for the same email, and confirm the new link works while the old one is
refused.

**Acceptance Scenarios**:

1. **Given** an expired or already-used link, **When** the user opens it, **Then**
   the system shows a localized "link invalid or expired" message and offers a way
   to request a new one.
2. **Given** a user requests a second link within the cooldown window, **When**
   the request is made, **Then** the system does not issue or send another link
   until the cooldown has elapsed, while still showing a neutral confirmation.

---

### Edge Cases

- **Unknown / unregistered email**: The confirmation message and observable
  timing MUST be indistinguishable from the registered-email case (no account
  enumeration). No link is sent.
- **Email address with no active credential path**: If an email cannot receive a
  link (e.g., not a deliverable channel), the user still sees the neutral
  confirmation; no error reveals the account's state.
- **Unverified account email**: A recovery request for an existing account whose
  email is not yet verified MUST return the neutral confirmation and MUST NOT
  send a reset link (the account belongs to the separate verification flow).
- **Expired link**: Opening a link after its validity window shows a localized
  "invalid or expired" result and no password change occurs.
- **Reused link**: A link that has already completed a password change is
  refused on any subsequent open.
- **Concurrent links**: If a user requests multiple links, the behavior when an
  older link is opened after a newer one exists MUST be well-defined (see
  Assumptions).
- **Concurrent completion**: Two simultaneous attempts to complete the same link
  MUST result in exactly one password change, never two conflicting writes.
- **Password reused**: Setting a new password identical to the current one is
  handled gracefully (accepted or rejected consistently — see Assumptions).
- **Rapid repeat requests**: Repeated requests for the same email within the
  cooldown window do not send additional messages and do not leak that the email
  exists.
- **Tampered or malformed link**: A link whose secret does not verify is refused
  with the same "invalid or expired" result, revealing nothing about which part
  failed.
- **Locale**: The message and all screens are presented in the user's locale,
  falling back to the default locale when a translation is missing.

## Clarifications

### Session 2026-08-19

- Q: Beyond the per-email cooldown, should the request surface also enforce broader abuse throttling? → A: Per-email cooldown **and** a per-IP/client rate limit on the request surface.
- Q: Must the target email be verified for password recovery, or is an unverified address also eligible? → A: Recovery requires a verified email; unverified addresses get the neutral confirmation but no link.
- Q: For invited users, how is the initial-setup entry reached — auto-sent link, self-service request, or something else? → A: The existing email-verification link is the entry point for initial setup; there is no separate setup-link request surface for invitees.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a recovery request surface where a user can
  initiate password recovery by providing an email address, without requiring the
  user to be signed in.
- **FR-001a**: On the recovery request surface, an email is *eligible* for a reset
  link only when it is a **verified** email on an existing account. A request MUST
  NOT issue a link to an unverified account email or an unknown address; those
  receive the same neutral confirmation but no link.
- **FR-001b**: Initial password setup for an invited user MUST be reached through
  the invitation's **existing email-verification link**, not the recovery request
  surface; the system MUST NOT expose a separate "request a setup link" entry
  point for invitees.
- **FR-002**: When a recovery request targets an eligible email (per FR-001a), the
  system MUST deliver a message containing a single-use, time-limited link that
  lets the recipient set a new password.
- **FR-003**: The system MUST respond to every recovery request with a
  neutral confirmation that does not reveal whether the email is registered or
  what state the account is in (no account enumeration).
- **FR-004**: The link's secret MUST be unguessable, MUST be stored only in a
  non-reversible form, and MUST be verified in a way that is not vulnerable to
  timing-based guessing.
- **FR-005**: Each link MUST be usable at most once to complete a password
  change; after a successful change the link MUST be invalidated.
- **FR-006**: Each link MUST expire after a bounded validity window; opening it
  after expiry MUST NOT allow a password change.
- **FR-007**: The system MUST enforce a cooldown between successive link requests
  for the same email so the flow cannot be used to spam a recipient or probe for
  accounts.
- **FR-007a**: In addition to the per-email cooldown, the request surface MUST
  enforce a per-IP/client rate limit so a single client cannot fire link requests
  at many different addresses (email-bombing or enumeration probing). The default
  limit is **10 requests per 10-minute window per client IP** (configurable);
  requests exceeding the limit MUST be refused without revealing account state,
  consistent with the neutral-confirmation behavior.
- **FR-008**: When completing the flow, the system MUST require the new password
  to satisfy the same password policy enforced elsewhere in the product, and MUST
  reject non-compliant passwords server-side with a localized explanation.
- **FR-009**: The new password MUST be stored using the same one-way,
  salted, adaptive hashing used for all account passwords — never in plaintext or
  reversible form.
- **FR-010**: On successful completion, the user's credential MUST be updated so
  that the new password works and any previous password no longer does.
- **FR-011**: Opening an invalid, expired, tampered, or already-used link MUST
  produce a single, localized "invalid or expired" outcome that does not
  distinguish between those causes.
- **FR-012**: All user-facing text in this flow (screens, buttons, validation
  messages, and the email message) MUST be localized by message key with a
  default-locale translation, and MUST fall back to the default locale when a
  translation is missing.
- **FR-013**: The system MUST record security-relevant events for this flow
  (link requested, link consumed, password changed, and failed/expired
  attempts) in a structured form that contains no secrets, raw links, or
  plaintext passwords.
- **FR-014**: Completing a password change MUST invalidate the user's other
  active sessions so a leaked or shared old session cannot outlive the reset.
- **FR-015**: After a successful password change the system MUST bring the user
  to a signed-in or ready-to-sign-in state consistent with the product's other
  post-credential flows.
- **FR-016**: The capability MUST be exposed as a user-facing web flow only for
  this feature. It is NOT a public API surface, so it does not require a separate
  remote (REST) implementation; the underlying logic may still live in the shared
  logic layer, but no new externally-consumed API contract is introduced here.
- **FR-017**: "Initial password setup" MUST be available only for invited users
  reached via a pending workspace/email channel (an invitation not yet claimed),
  and MUST be entered through that invitation's email-verification link (per
  FR-001b). Admin-created accounts and any other account states are out of scope
  for the initial-setup path in this feature.

### Key Entities *(include if data involved)*

- **Password action request**: A short-lived, single-use grant that authorizes
  one password change for one account. Holds only a non-reversible form of its
  secret, the target account/channel it applies to, its purpose (recover vs.
  initial setup), a creation time, and an expiry time. Deleted or invalidated
  once used or expired.
- **Identity user credential**: The account's password, held only as a one-way
  salted hash. Updated as the end result of a completed request.
- **Email channel**: The email address that receives the link; may be attached to
  a user or pending (e.g., an invitation not yet claimed).
- **Notification message**: The localized email carrying the link, produced from a
  message template rather than hard-coded copy.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user who has forgotten their password can regain access —
  from starting the request to signing in with a new password — in under 5
  minutes, without contacting support.
- **SC-002**: 100% of recovery requests return the same neutral confirmation
  (identical message text) regardless of whether the email is registered, and
  the request handler follows the same code path with no data-dependent
  short-circuit and sends email asynchronously so latency does not correlate
  with account existence. (Perfect constant-time is not claimed; the guarantee
  is no message-text difference and no deliberate early-return branch that
  reveals existence.)
- **SC-003**: 100% of links are rejected after their first successful use and
  after their expiry window elapses.
- **SC-004**: Repeated requests for the same email within the cooldown window
  result in at most one delivered message per cooldown period.
- **SC-004a**: A single client/IP exceeding the request rate limit is refused
  further requests within the limit window, with no observable difference that
  reveals whether any targeted address is registered.
- **SC-005**: 100% of password-change completions store the password as a
  one-way hash; no reversible or plaintext password is ever persisted or logged.
- **SC-006**: All screens and the email message render correctly in every
  supported locale, with missing translations falling back to the default locale
  and never showing a raw message key.
- **SC-007**: Measured reduction in manual/support-assisted password resets after
  the self-service flow ships (target: the flow handles the large majority of
  reset needs without human intervention).

## Assumptions

- **Enumeration protection**: The flow deliberately does not disclose whether an
  email is registered; the confirmation is identical for known and unknown
  addresses. This is a security requirement, not a UX gap.
- **Validity window & cooldown**: The link's expiry and the request cooldown reuse
  the service's existing action-link conventions (bounded link lifetime on the
  order of a day, and a short per-email request cooldown on the order of minutes)
  unless product decides otherwise.
- **Password policy reuse**: The new password is validated against the existing
  product password policy (minimum length and character-class rules) using the
  same rules on both the entry screen and the server; no new policy is introduced.
- **Single active link**: Issuing a new link supersedes prior outstanding links
  for the same purpose and email; only the most recent unexpired link is expected
  to complete, and older ones resolve to "invalid or expired".
- **Post-completion state**: On success the user is signed in (or immediately able
  to sign in), consistent with existing provisioning/verification flows.
- **Delivery channel**: The link is delivered by email; other channels (SMS,
  messenger) are out of scope for this feature.
- **Surface scope**: This is a user-facing web (Vaadin) flow only; no REST API
  surface is added, so the dual embedded/remote implementation obligation of
  constitution Principle VI is not triggered by this feature.
- **Initial-setup scope**: "Initial setup" covers only invited users with a
  pending workspace email channel, and is entered through that invitation's
  existing email-verification link rather than a dedicated setup-link request
  surface. Admin-created accounts and other account states are out of scope for
  the initial-setup path.
- **Localization scope**: Supported locales are the product's current set
  (default locale plus the other provided locale); email copy lives in a
  localizable template rather than in code.
- **Reuse of existing infrastructure**: This feature is expected to build on the
  service's existing secure action-link mechanism, email delivery/queue, password
  hashing, password-entry form, and authentication helpers rather than
  introducing new external services.
- **Same-password handling**: Setting a new password equal to the current one is
  treated consistently (permitted, since the account state is otherwise
  unchanged), unless product requires blocking reuse.
