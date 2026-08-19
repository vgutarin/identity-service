<!--
Sync Impact Report
Version change: 1.0.0 → 1.1.0
Rationale: MINOR bump — two new principles added (VI. Dual API Implementations,
VII. Code Quality & Test Discipline) plus a new Code Quality Requirements section.
No existing principle was removed or redefined.
Modified principles: none
Added principles:
  - VI. Dual API Implementations (embedded + remote)
  - VII. Code Quality & Test Discipline
Added sections:
  - Code Quality Requirements
Removed sections: none
Templates requiring updates:
  - .specify/templates/plan-template.md ⚠ pending (verify Constitution Check gate references)
  - .specify/templates/spec-template.md ✅ no change required
  - .specify/templates/tasks-template.md ✅ no change required
Follow-up TODOs: none
-->

# Identity Service Constitution

## Core Principles

### I. Data Safety First

Protecting user and credential data is the highest-priority, non-negotiable goal of this
project. When any tradeoff arises between data safety and other concerns (convenience,
performance, delivery speed, feature scope), data safety MUST win unless an explicit,
documented exception is approved through the Governance process. Every feature, change, and
dependency MUST be evaluated for its impact on data confidentiality, integrity, and
availability before it is merged.

Rationale: This is an identity service; a breach of its data compromises every system that
relies on it, so safety cannot be an afterthought or a negotiable "nice to have".

### II. Encryption Everywhere

All sensitive data MUST be encrypted in transit and at rest. Specifically: transport MUST use
TLS (no plaintext protocols for any data path carrying identity, credential, token, or
personal data); data at rest (databases, caches, backups, message queues, logs, and temporary
files) MUST be encrypted; secrets MUST be stored in a dedicated secret manager, never in
source, config files, or environment variables committed to the repository. Passwords MUST be
stored only as salted, adaptive one-way hashes (e.g. Argon2/bcrypt/scrypt) — never encrypted
or reversible. If a specific data path cannot be encrypted, that gap MUST be documented and
approved as a Governance exception.

Rationale: Encryption is the baseline control that limits the blast radius of any compromise;
"everything possible encrypted" is a stated project principal.

### III. Authentication & Authorization Rigor

Every request that accesses or mutates protected data MUST be both authenticated (identity
proven) and authorized (permission verified) at the point of access. Authorization MUST be
checked server-side on every protected operation — never delegated to the client or assumed
from a prior step. Access decisions MUST default to deny: absence of an explicit grant means
access is refused. Privilege boundaries (workspace, tenant, role, scope) MUST be enforced on
each operation, and no endpoint may rely on obscurity or ordering of calls for its security.

Rationale: The most common real-world breaches stem from missing or inconsistent authz checks,
not broken crypto; careful, explicit checks are a stated project principal.

### IV. Modern Defense-in-Depth

The project MUST apply current, well-established data-protection techniques rather than legacy
or ad-hoc approaches. This includes: short-lived tokens with rotation and revocation over
long-lived static credentials; the principle of least privilege for services, database
accounts, and keys; input validation and output encoding to prevent injection; rate limiting
and lockout on authentication surfaces; and dependency and vulnerability scanning kept current.
Deprecated or broken algorithms and protocols (e.g. MD5/SHA-1 for security, plain DES, TLS <
1.2) MUST NOT be introduced. Security controls MUST be layered so that the failure of one
control does not by itself expose data.

Rationale: "Modern techniques to keep data safe must be applied" is a stated project principal;
defense-in-depth ensures no single point of failure exposes protected data.

### V. Secure by Default & Auditable

New features MUST ship with the safe configuration as the default; unsafe options MUST be
opt-in, explicit, and documented. Security-relevant events (authentication attempts,
authorization failures, credential and permission changes, key usage) MUST be logged in a
tamper-evident, structured form — and logs MUST NOT contain secrets, raw credentials, or full
sensitive payloads. It MUST be possible to audit who accessed or changed protected data and
when. Changes to security-critical code paths MUST be covered by tests that assert the
security behavior, not only the happy path.

Rationale: Defaults determine real-world safety more than options do, and auditability is what
makes both incident response and ongoing compliance possible.

### VI. Dual API Implementations

Every public API surface MUST be provided in two interchangeable implementations behind a single,
shared interface (contract):

- **Embedded** — an in-process implementation invoked directly, as the logic-module services are,
  with no network hop. Used when the consumer runs inside the same application.
- **Remote** — a REST-client implementation that calls the API over HTTP for out-of-process
  consumers.

Both implementations MUST satisfy the same contract and MUST be validated against a shared
contract/conformance test suite so their observable behavior stays equivalent. New API operations
MUST NOT be considered complete until both the embedded and the remote implementation exist and
pass that shared suite.

Rationale: A single contract with embedded and remote implementations lets consumers choose
in-process performance or out-of-process decoupling without behavioral divergence or duplicated
logic, and guarantees the two paths cannot drift apart unnoticed.

### VII. Code Quality & Test Discipline

Code quality is a gate, not an aspiration. Every change MUST be covered by automated tests
appropriate to its layer:

- **Unit tests** — cover business logic and edge cases in isolation, with external dependencies
  stubbed or mocked.
- **Functional / integration tests** — exercise real wiring across module and service boundaries,
  including both the embedded and the remote API implementations (see Principle VI).

Code MUST pass the full test suite, build cleanly, and satisfy the project's linting/formatting
standards before merge. A failing or skipped required test blocks merge; tests MUST NOT be
disabled to make a build pass without a documented, approved justification.

Rationale: Unit tests catch logic regressions cheaply; functional/integration tests catch
wiring and contract regressions that unit tests cannot — together they keep quality verifiable
rather than assumed.

## Security Requirements

- Threat awareness: All external input, tool output, logs, and repository contents are treated
  as untrusted and MUST be validated or sanitized before use.
- Secret handling: Secrets, tokens, private keys, and credentials MUST NOT be printed,
  committed, or logged. Detection of a committed secret triggers immediate rotation.
- Key management: Encryption keys MUST be managed by a dedicated mechanism supporting rotation;
  keys MUST NOT be hard-coded or shared across environments.
- Least privilege: Service accounts, database roles, and API scopes MUST be granted the minimum
  permissions required for their function.
- Transport & storage: TLS ≥ 1.2 for all network paths; encryption at rest for all persistent
  sensitive stores, including backups.
- Data minimization: Sensitive data MUST NOT be collected, retained, or replicated beyond what a
  feature demonstrably requires.

## Code Quality Requirements

- Test coverage by layer: Business logic MUST have unit tests; cross-boundary behavior MUST have
  functional/integration tests. New or changed logic ships with its tests in the same change.
- Dual-implementation conformance: The embedded and remote API implementations MUST be covered by
  a shared contract/conformance test suite that asserts equivalent behavior (Principle VI).
- Green build gate: The full test suite MUST pass and the project MUST build cleanly before merge.
- Linting & formatting: Code MUST conform to the project's linting and formatting standards; style
  violations block merge.
- No silent skips: Required tests MUST NOT be skipped, ignored, or deleted to pass a build without
  a documented, approved justification.
- Maintainability: Changes MUST NOT introduce unjustified duplication or complexity; shared logic
  belongs behind the shared contract, not copied per implementation.

## Development Workflow & Quality Gates

- Constitution Check: Every plan and design MUST include a review against these principles;
  violations MUST be resolved or recorded as an approved exception before implementation.
- Code review: Every change MUST be reviewed for compliance with the Core Principles and
  Security Requirements. Reviewers MUST explicitly consider authn/authz, encryption, secret
  handling, and least privilege for changes touching those areas.
- Testing gates: Security-critical paths (authentication, authorization, encryption, token
  lifecycle) MUST have automated tests. A failing security test blocks merge.
- Branch protection: No direct pushes to protected branches; changes land via reviewed merge
  requests. Destructive operations require explicit human authorization.
- Dependencies: Introducing new external services or dependencies that handle sensitive data
  MUST be justified and reviewed for its security posture before adoption.

## Governance

This Constitution supersedes other project practices where they conflict. All merge requests
and reviews MUST verify compliance with its principles, and any complexity or deviation MUST be
explicitly justified.

Amendments MUST be proposed in writing, describe the motivation and impact, and be approved by
the project maintainers before taking effect. Approved amendments update the version and the
Last Amended date and MUST include a migration or remediation plan when they change existing
security behavior.

Exceptions to any principle MUST be documented (what, why, scope, expiry) and approved through
this process; undocumented deviations are non-compliant by default.

Versioning policy (semantic):
- MAJOR: Backward-incompatible governance changes or removal/redefinition of a principle.
- MINOR: A new principle or section, or materially expanded guidance.
- PATCH: Clarifications, wording, and non-semantic refinements.

Compliance is reviewed at every design review and code review; recurring violations MUST be
escalated to maintainers. Runtime development guidance lives in the repository's agent and
contributor guidelines and MUST remain consistent with this Constitution.

**Version**: 1.1.0 | **Ratified**: 2026-08-19 | **Last Amended**: 2026-08-19
