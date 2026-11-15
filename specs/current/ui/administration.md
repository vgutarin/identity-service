# Administration UI

## Purpose and actors

The authenticated Vaadin UI lets OWNER users manage workspaces, applications, API keys, roles, members,
role templates, claims, and user permissions. Vaadin route security complements domain authorization.

## Routes and entry conditions

| Route | Purpose |
| --- | --- |
| `/`, `/about` | Application shell and About. |
| `/admin/workspaces` | List/create workspaces. |
| `/admin/workspaces/:workspaceId` | Edit workspace; applications, roles, and users tabs. |
| `/admin/applications/:applicationId` | Read-only application details, member list, and message history. |
| `/admin/users` | User overview. |
| `/admin/users-channels` | User-channel overview. |
| `/admin/user-permissions` | Inspect/change user permissions. |
| `/admin/role-templates` | Manage role templates. |
| `/admin/application-claims` | Manage application-user claims. |

## Interactions and outcomes

Workspace detail links back to the list. Its tabs manage applications, roles, and member channels.
Application dialogs create/edit/delete Telegram bots and manage API keys. Issuance displays the key
secret once for copying; later lists show metadata/revocation. Bot save confirms persistence and queued
setup, not Telegram registration. Previous-webhook cleanup can fail the save synchronously.

### Webhook controls

Opening Applications or selecting **Refresh webhooks** starts one background batch; results appear
individually. Rendering uses cached results. The localized **Webhook** column uses text plus color:

| State | Interaction |
| --- | --- |
| **Not applicable** | Non-Telegram application. |
| **Loading** / **Updating** | Work pending; duplicate action/refresh submissions disabled. |
| **Unknown** | Inspection/action failed (including missing public URL or full queue); toggle disabled. |
| **No** | Click to enable; a missing bot ID is validated/stored first. |
| **Yes** / **Yes, incorrect URL** | Click to remove when a validated bot ID exists. |

Clicks require configured webhook support/public URL and `app.update`. Tooltips explain the action,
including removal of an incorrect URL; no confirmation dialog is shown. Success requires re-querying
Telegram. Failure shows a generic localized error and **Unknown**, without remote details or retry.
Inspection reflects URL agreement only, not secret validity or delivery health.

Version changes from bot-ID backfill update the row and subsequent actions/Edit without a page reload.
Refreshes, workspace/bot changes, navigation, and toggles discard obsolete results; started remote work
can still finish. While the tab has pending work, temporary UI polling delivers results, restoring the
previous interval afterward. It does not query Telegram periodically.

Removal has no persistent disable switch: saves, pending SET commands, or startup registration can
restore it. [Administration logic](../logic/identity-administration.md) owns version/permission checks,
remote consistency, and lifecycle cleanup; [engineering notes](../engineering/engineering-notes.md#background-administration-inspection-and-toggle)
own executor and callback mechanics.

Other writes show localized success notifications or shared localized errors. Role screens manage
roles/permissions; workspace Users manages membership.

### Application details

Application names in the workspace grid link to the OWNER-only details page. The header shows name,
ID, URI, and creation/update dates; configuration payloads and credentials are excluded. Back navigation
returns to the owning workspace's Applications tab. Invalid/missing applications show a localized
unavailable state with navigation to Workspaces.

Members is the default tab (`?tab=members`); Messages uses `?tab=messages`. Only permitted tabs appear:
Members requires `app.read`, Messages `app.message.read`. Permissions are checked on activation/refresh;
hidden tabs do not load data, and unauthorized/invalid requested tabs fall back to the first permitted tab.
OWNER retains the existing permission bypass. No permitted tabs produces a localized access message.
Permission changes insert/remove only affected tabs. Refresh and paging retain tab components and
selection when permissions are unchanged. Selecting a tab replaces the current URL through Vaadin's
History API, retaining the application path and other query parameters without navigating again.

Members shows name, user ID, and first/last authentication. Messages shows message/recipient IDs,
localized status/failure reason, and timestamps, with Previous/Next paging (50 rows) and newest-first
ordering. Both have manual refresh, empty states, and sanitized load errors; there is no automatic polling.
Message history includes every submission source and survives recipient membership removal.

The members grid shows an Actions column only with `app.message.send` for the selected application.
Its envelope icon has a localized Send Message tooltip and accessible label. Opening it rechecks SEND
and shows the recipient, a required multiline text field, Cancel, and Send. Message-history READ is
independent and is not required to submit. Cancel/Escape closes without submitting; outside clicks do
not discard a draft. Send validates the [plain-text content contract](../api/identity-application-api.md#application-messages)
without trimming or interpreting markup, then calls the permission-gated administration service with
the selected application/member and a generated UUID key. Send is disabled while submitting; success
closes the dialog and confirms submission for asynchronous delivery, without claiming delivery success.
Validation, permission, unavailable-recipient, and unexpected failures show localized safe feedback and
retain the draft. An explicit retry of unchanged content reuses its key; editing content uses a new key.
Refreshing rechecks column visibility; the service rechecks SEND at submission, including for an open dialog.

All new labels, browser/shell titles, paging counts, states, and feedback support Ukrainian and English.
Dates/numbers use the selected locale with existing timezone behavior. The locale picker reloads the
same route/tab; identifiers, application names, URI values, and URL parameters are not translated.

## Presentation and platform constraints

The shell provides navigation; views use Vaadin 25.2 and the shared localization provider.

## Related contracts and evidence

- [Administration domain](../logic/identity-administration.md).
- [Workspace detail](../../../identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/admin/IdentityWorkspaceDetails.java),
  [applications tab](../../../identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/admin/IdentityWorkspaceApplicationsTab.java),
  [webhook indicators](../../../identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/admin/TelegramWebhookIndicators.java).
- [Indicator tests](../../../identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/admin/TelegramWebhookIndicatorsTest.java)
  and [route security](../../../identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/config/SecurityConfiguration.java).
- [Application details](../../../identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/admin/IdentityApplicationDetails.java),
  [isolated view tests](../../../identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/admin/IdentityApplicationDetailsUnitTest.java),
  [message dialog tests](../../../identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/admin/IdentityApplicationMessageDialogTest.java),
  and [navigation/localization tests](../../../identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/admin/IdentityApplicationDetailsTest.java).
