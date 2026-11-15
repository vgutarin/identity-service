# Open decisions

Unresolved bugs, choices, implementation work, and verification gaps. Close entries when their work
is verified; preserve significant architectural rationale in [ADRs](decisions/).

## Documentation governance

The original root README requested a constitution principle requiring UI features in usage docs, API
features in API docs, and navigation to current guidance within three links from the root. The current
AGENTS/spec-authoring rules cover contract maintenance, and the root now links UI/API guides, but the
constitution amendment has not been made. Preserve that task until explicitly resolved through
[constitution governance](../../.specify/memory/constitution.md#governance).

## REST client submission retries

Client connection/read timeouts are implemented. The original README's question about automatic client
retries remains undecided. Decide whether to add them; any submission retry must reuse the original
idempotency key and must not change the single-attempt delivery policy.

## Align embedded and REST client submission exceptions

Embedded `submitMessage` throws public `identity-api` exceptions; the typed REST client propagates Spring
HTTP errors for the same failures.

**Recommendation:** match status and problem code before translating:

| Response | Public exception |
| --- | --- |
| `409` + `MESSAGE_IDEMPOTENCY_CONFLICT` | `IdentityMessageIdempotencyConflictException` |
| `422` + `MESSAGE_DELIVERY_UNAVAILABLE` | `IdentityMessageDeliveryUnavailableException` |

Keep generic HTTP handling for unknown/missing/malformed problem bodies and other errors. Translated
exceptions must retain safe messages without the original response body or HTTP cause.

**Done when:** client unit and embedded/remote integration tests cover both translations and fallback,
and the [API contract](api/identity-application-api.md#application-messages) describes parity.
[Client source](../../identity-rest-client/src/main/java/vg/identity/rest/v1/IdentityApplicationApiRestClient.java).

## Messaging queue and concurrency

The owner accepts the bounded lookback's residual late-commit risk; its
[queue contract and permanent tests](engineering/engineering-notes.md#command-queue-and-message-storage)
cover the limits. Replacing the cursor or adding a queue index is not planned.

Concurrent queue-claim regression coverage remains outstanding.

**Done when:** permanent MySQL tests verify competing claim transactions cannot both claim the same
command, including the optimistic-lock loser. Preserve atomic submission and no automatic delivery retry.

## Telegram phone disclosure replay

Phone processing persists no Telegram update ID. A valid old contact replay after application-scoped
revocation can set the disclosure flag again. Repeating a contact is idempotent only without an intervening
revocation; current handling does not establish freshness.

**Decision needed:** define a deduplication/freshness boundary that retains ordinary repeat delivery while
preventing a revoked share from being replayed as new consent. Revisit before adding non-idempotent webhook
side effects (notifications, counters, or workflow transitions).

**Done when:** a permanent share → revoke → replay regression test verifies the chosen rule, including
transaction rollback and genuinely new shares, and current phone contracts reflect it. A changed
accepted architectural choice requires a new/superseding ADR rather than rewriting ADR-0001.
[Phone processor](../../identity-logic/src/main/java/vg/identity/service/TelegramPhoneWebhookService.java),
[disclosure decision](decisions/0001-telegram-shared-phone-disclosure.md).
