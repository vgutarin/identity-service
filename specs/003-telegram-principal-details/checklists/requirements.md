# Specification Quality Checklist: Consent-Gated Principal From Telegram Data

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-21
**Last updated**: 2026-08-27
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
- **Correction incorporated**: consent to keep personal data is now the gating concept — no consent
  means null unique identifier and zero persistence; consent triggers user creation and a non-null
  identifier (FR-002 through FR-007, SC-001 through SC-003).
- **Clarified (2026-08-23 session)**: (1) consent-gating **replaces** the existing Telegram auth
  operation in place — accepted breaking change, correcting the current no-consent provisioning
  (FR-021); (2) naming resolved — a **single** "principal" object with a consent indicator + nullable
  identifier (not two types); (3) consent-triggered user creation **and** authentication are audited,
  with no secrets/personal data in audit entries (FR-022, FR-023, SC-010).
- **OIDC compatibility dropped (2026-08-27 session)**: the returned principals — both the application
  principal and the Telegram user principal — are **no longer required to be OIDC-compatible**. The
  earlier OIDC-claim mapping (`sub`, `name`, `locale`) is removed; identity fields now use
  domain-native, self-explaining names (FR-017). OIDC (claim shape and protocol) is recorded as fully
  out of scope, not merely deferred.
- **Typed, self-explaining identifiers (2026-08-27 session)**: every unique identifier is the system's
  unique-identifier type (UniqueId) with a self-explaining name (e.g. `userUniqueId`,
  `applicationUniqueId`, `workspaceUniqueId`), never a raw string or number (FR-018); the application
  principal's location is a URI value type, not a raw string (FR-019); identifiers must be stable and
  never reassigned (FR-020). SC-009 was rewritten accordingly.
- **Altitude note**: the spec deliberately names two concrete value types — `UniqueId` (the domain's
  identifier concept) and `URI` (a standard address value) — because the product decision is
  explicitly about identifier representation. This mirrors the earlier spec's use of OIDC claim names
  and is intentional, not leaked framework detail.
- **Additional data reduced to a placeholder**: no derivation, population, or persistence of locale
  (or any additional data) is in scope — the contract only reserves the slot (FR-010, FR-011; US3
  reframed; SC-005). Populating it is recorded as deferred.
