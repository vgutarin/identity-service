# Contract: REST API Authentication (API Key)

Documents the existing API-key authentication contract that governs `/api/**` when the REST API is
enabled. This feature does **not** change this contract; it ensures the same contract applies whether
the REST API is hosted by the standalone app or by the Vaadin app.

## Authentication

- **Header**: `X-VG-Identity-API-Key` (single value).
- **Validation**: the value is resolved to a principal by `IdentityApiKeyService`.
- **Scope matcher**: the API-key `SecurityFilterChain` matches `securityMatcher("/api/**")` and runs at
  `@Order(1)`, ahead of the Vaadin security chain.
- **Session policy**: stateless (`SessionCreationPolicy.STATELESS`); CSRF/formLogin/httpBasic disabled
  for the `/api/**` matcher only.

## Guarantees

- **A1 (key required)**: A request to `/api/**` with no `X-VG-Identity-API-Key` header, more than one
  value, or an invalid key receives `401 Unauthorized` and is not processed by any controller.
- **A2 (authorized principal)**: A valid key establishes a `PreAuthenticatedAuthenticationToken` whose
  authorities gate access; `anyRequest().authenticated()` is enforced.
- **A3 (precedence in Vaadin host)**: In the Vaadin app, `/api/**` is handled exclusively by this chain,
  never by Vaadin's `permitAll`/anonymous rules.
- **A4 (no credential leakage)**: The API key value is never written to logs or error responses.
- **A5 (equivalence)**: An operation invoked over REST returns results equivalent to the same operation
  performed via the embedded `IdentityApplicationApi` implementation.

## Endpoints in scope (unchanged behavior)

| Method | Path | Contract method |
|--------|------|-----------------|
| GET | `/api/v1/applications/me` | `IdentityApplicationApi.me()` |
| POST | `/api/v1/applications/me/authentications/telegram` (text/plain body) | `IdentityApplicationApi.authenticateTelegram(initData)` |

> `IdentityUserController` is commented out in the codebase and is out of scope for this feature.

## Acceptance mapping

- FR-007 → A1, A2, A3, A4
- FR-008, SC-003 → A5
- SC-004 → A1
