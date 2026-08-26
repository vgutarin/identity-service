# Research: Consent-Gated Principal From Telegram Data

**Feature**: 003-telegram-principal-details | **Date**: 2026-08-27

No `NEEDS CLARIFICATION` markers remained in the spec after `/speckit-clarify`. The decisions below
are design choices grounded in the existing codebase and the constitution. Decision 7 was revised on
2026-08-27 when OIDC compatibility was dropped in favor of domain-native, typed identifiers.

## Decision 1 — Response type: reshape the existing principal record

**Decision**: Reshape `IdentityApplicationUserPrincipal` (in `identity-api`) into the domain-native
principal returned by the Telegram operation, with fields:
`userUniqueId` (`UniqueId`, nullable), `name`, `permissions` (flat `Set<String>`), `consentGiven`
(boolean), and a reserved optional `preferredLocale` (always absent for now). Not OIDC-compatible
(decided 2026-08-27).

**Rationale**: The record already *is* the endpoint's response object, and the clarification session
chose a single principal type spanning both states. Reshaping keeps one type (no proliferation), uses
the system's typed identifier (`UniqueId`) rather than a raw string, and flattens permissions from the
current `claimsByScope[PERMISSIONS_SCOPE]`.

**Alternatives considered**:
- *New separate DTO, keep the claims-scoped record*: rejected — adds a second "principal" type,
  conflicting with the single-object decision.
- *Keep `claimsByScope` map as-is*: rejected — it buries permissions/`name`/`userUniqueId` in an
  untyped map.

**Follow-ups**: The shared `PERMISSIONS_SCOPE` constant (used by Vaadin admin and claim tests) must
survive the reshape — keep it on the record or relocate to a neutral home (e.g. the claim service) and
update the ~3 references. Permissions are derived as
`findClaimsByScope(app, userUniqueId).get(PERMISSIONS_SCOPE)` flattened to a `Set<String>`.

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
principal body in *both* states: pre-consent (`userUniqueId` null, `consentGiven=false`) and established
(`userUniqueId` present, `consentGiven=true`).

**Rationale**: Preserves the existing contract's failure representation; distinguishes "not resolvable"
(204) from "resolved but unconsented" (200 + null `userUniqueId`) exactly as the spec's states require.

**Alternatives considered**: 4xx error for invalid data — rejected; leaks validity signal and breaks
the current neutral-failure posture.

## Decision 4 — Consent-aware provisioning in `identity-logic`

**Decision**: Add a consent-aware resolution path. Behavior:
1. Verify init data → Telegram user (existing `TelegramAuthenticationService`).
2. Look up user by Telegram id.
3. **Found + consent already recorded** → established principal (`userUniqueId`, permissions).
4. **Found + no recorded consent + consent granted now** → set `consentToKeepPersonalDataAt`, audit,
   return established principal.
5. **Found + no recorded consent + consent not granted** → pre-consent principal (`userUniqueId` null);
   persist nothing new.
6. **Not found + consent granted** → create user (anonymous entity + Telegram binding) with
   `consentToKeepPersonalDataAt` set, audit creation, return established principal.
7. **Not found + consent not granted** → pre-consent principal (`userUniqueId` null); persist nothing.

**Rationale**: Places the safety gate at the point of persistence (`createAnonymousEntity` /
`bindTelegramUser` today run unconditionally in `resolveTelegramUser`). Steps 4–5 also handle legacy
rows (see Decision 5).

**Alternatives considered**: Gating only in the API service while leaving `resolveTelegramUser`
unconditional — rejected; leaves a reachable no-consent provisioning path (defense-in-depth).

## Decision 5 — Legacy anonymous Telegram users (pre-change rows)

**Decision**: Treat consent state strictly by the stored `consentToKeepPersonalDataAt`. Existing
Telegram users provisioned by the old unconditional path have it null; they are treated as **not yet
consented** — resolving them without consent returns a pre-consent principal (null `userUniqueId`), and
the first call with consent backfills `consentToKeepPersonalDataAt`. No bulk migration or deletion is
performed by this feature.

**Rationale**: Keeps the invariant "non-null `userUniqueId` ⇒ consent given" (FR-007) true even for legacy
rows, without destructive migration. Cleanup of orphaned no-consent rows is out of scope.

**Alternatives considered**:
- *Backfill consent for all existing rows*: rejected — fabricates consent that was never given.
- *Delete legacy no-consent rows now*: rejected — destructive; not required by the spec and outside
  scope.

## Decision 6 — Audit via the existing security-audit logger

**Decision**: Emit structured records through the `vg.identity.security.audit` logger (same channel as
`ApiAuthenticationAuditor`): one event for consent-triggered user creation and one for the Telegram
authentication success, e.g. `event=telegram.user.provision outcome=CREATED userUniqueId=<id> ...` and
`event=telegram.auth outcome=SUCCESS userUniqueId=<id> ...`. Reference the user by stable `userUniqueId`
only; never log init data, profile fields, or the API key. Sanitize any request-derived values against
log injection.

**Rationale**: Reuses the constitution-aligned (Principle V), tamper-evident, PII-free audit pattern
already in the repo (FR-022, FR-023).

**Alternatives considered**: New audit sink/mechanism — rejected; unnecessary divergence from the
established pattern.

## Decision 7 — Domain-native, typed identifiers (no OIDC)

**Decision** (revised 2026-08-27): The principals are **not** OIDC-compatible. Identity fields use
domain-native, self-explaining names and the system's `UniqueId` type:
- Telegram user principal: `userUniqueId: UniqueId` (nullable, stable, non-reassigned).
- Application principal (`me()`): `applicationUniqueId: UniqueId`, `workspaceUniqueId: UniqueId`,
  `uri: URI`.

`UniqueId` and `URI` are carried on the wire as strings with **no custom Jackson config**: `UniqueId`
already declares `@JsonValue` on `toString()` (encoded string form) and `@JsonCreator(DELEGATING)` on
`parse(String)`, and `URI` uses Jackson's built-in string handling. `permissions` and `consentGiven`
are ordinary fields. The reserved locale placeholder is named `preferredLocale` (domain-native),
unpopulated.

**Rationale**: Matches the 2026-08-27 clarification and FR-017–FR-020. Typed identifiers prevent
raw-string/number mix-ups and make each identifier self-describing; `URI` gives callers an
already-validated address. `UniqueId` is `Serializable`, so `IdentityApplicationUserPrincipal` remains
`Serializable`.

**Alternatives considered**:
- *Keep OIDC claim shape (`sub`/`name`/`locale`)*: rejected — the product decision dropped the OIDC
  requirement; a future OIDC surface, if needed, can map these domain fields onto claims at its own
  boundary (spec "Out of Scope / Deferred").
- *Keep raw `String`/`Long` identifiers*: rejected — untyped and not self-explaining (FR-018).

## Decision 8 — Display name source

**Decision**: Derive `name` from the verified Telegram profile (`TelegramUserPrincipal.displayName()`:
first+last, else username) in both states, so the pre-consent principal needs no stored data.

**Rationale**: Identification must not require storage (US1); the derivation already exists.

**Alternatives considered**: Use the stored user's display name for established users — deferred;
adds a state-dependent source with no current benefit and risks divergence for legacy anonymous rows.
