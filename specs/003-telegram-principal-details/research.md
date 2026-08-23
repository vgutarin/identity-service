# Research: Consent-Gated Principal From Telegram Data

**Feature**: 003-telegram-principal-details | **Date**: 2026-08-23

No `NEEDS CLARIFICATION` markers remained in the spec after `/speckit-clarify`. The decisions below
are design choices grounded in the existing codebase and the constitution.

## Decision 1 — Response type: reshape the existing principal record

**Decision**: Reshape `IdentityApplicationUserPrincipal` (in `identity-api`) into the OIDC-compatible
principal returned by the Telegram operation, with fields:
`sub` (nullable), `name`, `permissions` (flat `Set<String>`), `consentGiven` (boolean), and a reserved
optional `locale` (always absent for now).

**Rationale**: The record already *is* the endpoint's response object, and the clarification session
chose a single principal type spanning both states. Reshaping keeps one type (no proliferation),
directly yields the OIDC claim shape, and flattens permissions from the current
`claimsByScope[PERMISSIONS_SCOPE]`.

**Alternatives considered**:
- *New separate DTO, keep the claims-scoped record*: rejected — adds a second "principal" type,
  conflicting with the single-object decision.
- *Keep `claimsByScope` map as-is*: rejected — it is not OIDC-shaped and buries permissions/`name`/`sub`.

**Follow-ups**: The shared `PERMISSIONS_SCOPE` constant (used by Vaadin admin and claim tests) must
survive the reshape — keep it on the record or relocate to a neutral home (e.g. the claim service) and
update the ~3 references. Permissions are derived as `findClaimsByScope(app, sub).get(PERMISSIONS_SCOPE)`
flattened to a `Set<String>`.

## Decision 2 — Transmit consent via a JSON request body

**Decision**: Replace the current `text/plain` init-data body with a JSON request body carrying both
the Telegram init data and the consent flag, e.g.
`{ "telegramInitData": "<raw>", "consentToKeepPersonalData": true }`.

**Rationale**: Consent must travel with the resolution request (FR-001). A JSON body keeps the
sensitive init data out of the URL/query (privacy), is extensible for future fields, and is a natural
fit for the typed client. The consent flag defaults to `false` when omitted (safe default).

**Alternatives considered**:
- *Keep `text/plain` body + `?consent=true` query param*: workable and non-PII, but splits one logical
  input across body+query and is less extensible.
- *Consent header*: non-obvious; headers are for cross-cutting metadata, not core request data.

## Decision 3 — "No principal" stays HTTP 204; both resolved states are 200

**Decision**: Invalid/unverifiable/non-resolvable Telegram data → **204 No Content** (unchanged
behavior, reveals nothing about which input failed, FR-008/FR-013). A resolved user → **200** with the
principal body in *both* states: pre-consent (`sub` null, `consentGiven=false`) and established
(`sub` present, `consentGiven=true`).

**Rationale**: Preserves the existing contract's failure representation; distinguishes "not resolvable"
(204) from "resolved but unconsented" (200 + null `sub`) exactly as the spec's states require.

**Alternatives considered**: 4xx error for invalid data — rejected; leaks validity signal and breaks
the current neutral-failure posture.

## Decision 4 — Consent-aware provisioning in `identity-logic`

**Decision**: Add a consent-aware resolution path. Behavior:
1. Verify init data → Telegram user (existing `TelegramAuthenticationService`).
2. Look up user by Telegram id.
3. **Found + consent already recorded** → established principal (`sub`, permissions).
4. **Found + no recorded consent + consent granted now** → set `consentToKeepPersonalDataAt`, audit,
   return established principal.
5. **Found + no recorded consent + consent not granted** → pre-consent principal (`sub` null); persist
   nothing new.
6. **Not found + consent granted** → create user (anonymous entity + Telegram binding) with
   `consentToKeepPersonalDataAt` set, audit creation, return established principal.
7. **Not found + consent not granted** → pre-consent principal (`sub` null); persist nothing.

**Rationale**: Places the safety gate at the point of persistence (`createAnonymousEntity` /
`bindTelegramUser` today run unconditionally in `resolveTelegramUser`). Steps 4–5 also handle legacy
rows (see Decision 5).

**Alternatives considered**: Gating only in the API service while leaving `resolveTelegramUser`
unconditional — rejected; leaves a reachable no-consent provisioning path (defense-in-depth).

## Decision 5 — Legacy anonymous Telegram users (pre-change rows)

**Decision**: Treat consent state strictly by the stored `consentToKeepPersonalDataAt`. Existing
Telegram users provisioned by the old unconditional path have it null; they are treated as **not yet
consented** — resolving them without consent returns a pre-consent principal (null `sub`), and the
first call with consent backfills `consentToKeepPersonalDataAt`. No bulk migration or deletion is
performed by this feature.

**Rationale**: Keeps the invariant "non-null `sub` ⇒ consent given" (FR-007) true even for legacy
rows, without destructive migration. Cleanup of orphaned no-consent rows is out of scope.

**Alternatives considered**:
- *Backfill consent for all existing rows*: rejected — fabricates consent that was never given.
- *Delete legacy no-consent rows now*: rejected — destructive; not required by the spec and outside
  scope.

## Decision 6 — Audit via the existing security-audit logger

**Decision**: Emit structured records through the `vg.identity.security.audit` logger (same channel as
`ApiAuthenticationAuditor`): one event for consent-triggered user creation and one for the Telegram
authentication success, e.g. `event=telegram.user.provision outcome=CREATED sub=<id> ...` and
`event=telegram.auth outcome=SUCCESS sub=<id> ...`. Reference the user by stable `sub` only; never log
init data, profile fields, or the API key. Sanitize any request-derived values against log injection.

**Rationale**: Reuses the constitution-aligned (Principle V), tamper-evident, PII-free audit pattern
already in the repo (FR-022, FR-023).

**Alternatives considered**: New audit sink/mechanism — rejected; unnecessary divergence from the
established pattern.

## Decision 7 — OIDC claim mapping (claim-shape only)

**Decision**: Map `sub` = user unique id (stable, non-reassigned), `name` = display name, `locale` =
reserved BCP-47 placeholder (unpopulated). `permissions` and `consentGiven` are non-standard fields
that MUST NOT reuse OIDC standard claim names. No token signing, discovery, or UserInfo endpoints.

**Rationale**: Matches FR-017–FR-020 and the deferral of full OIDC protocol support; keeps the claim
contract stable so a future OIDC feature is additive.

**Alternatives considered**: Emit a signed OIDC ID token now — rejected; explicitly deferred in the
spec, materially larger scope.

## Decision 8 — Display name source

**Decision**: Derive `name` from the verified Telegram profile (`TelegramUserPrincipal.displayName()`:
first+last, else username) in both states, so the pre-consent principal needs no stored data.

**Rationale**: Identification must not require storage (US1); the derivation already exists.

**Alternatives considered**: Use the stored user's display name for established users — deferred;
adds a state-dependent source with no current benefit and risks divergence for legacy anonymous rows.
