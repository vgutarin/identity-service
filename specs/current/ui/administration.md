# Administration UI

## Purpose and actors

The authenticated Vaadin application lets `OWNER` users administer workspaces, their applications and API
keys, workspace roles and members, role templates, and user permissions. Route access is enforced by
Vaadin's Spring Security integration and view access checking.

## Routes and entry conditions

| Route | Purpose |
| --- | --- |
| `/` and `/about` | Application shell and About view. |
| `/admin/workspaces` | List and create workspaces. |
| `/admin/workspaces/:workspaceId` | Edit one workspace and switch between its applications, roles, and users. |
| `/admin/users` | User overview. |
| `/admin/users-channels` | User-channel overview. |
| `/admin/user-permissions` | Inspect and change a user's permissions. |
| `/admin/role-templates` | Manage reusable role templates. |
| `/admin/application-claims` | Manage application-user claim values. |

## Interactions and outcomes

- Workspace management creates, edits, and deletes workspaces. Its detail screen links back to the workspace
  list and provides applications, roles, and user tabs for the selected workspace.
- Application management supports Telegram-bot applications, editing, deletion, and API-key management.
  A newly generated API-key secret is shown in the issuance dialog so it can be copied; later listings show
  metadata and allow revocation instead of re-showing the secret.
  Saving a bot queues webhook setup; the save notification confirms persistence and scheduling, rather than
  successful setup at Telegram. Replacing a bot can fail synchronously during previous-webhook cleanup,
  according to [Identity administration](../logic/identity-administration.md).
- The Applications grid has a localized, text-labelled **Webhook** column, with supplementary colors.
  Opening the Applications tab or clicking **Refresh webhooks** starts one background batch for its
  Telegram bots. Results appear individually as they arrive; rendering uses the cached results only.
  Non-Telegram applications show **Not applicable**. Inspection shows **Loading**; failures (including
  missing public URL or a full worker queue) show **Unknown**, with toggling disabled.
  A successful inspection shows **Yes**, **No**, or **Yes, incorrect URL**, based on Telegram's actual URL.
  This does not indicate secret correctness or delivery health.
- The webhook indicator is directly clickable, without confirmation, when support and the public URL are
  configured and the caller has `app.update` permission. **No** registers Identity's expected webhook,
  retrieving and storing a validated Telegram ID first if it is missing. Either **Yes** state removes the
  current webhook when the bot already has a stored validated ID; otherwise removal is disabled.
  Tooltips describe that action, including removal of an incorrect URL. The row shows **Updating** while
  its action runs. Duplicate actions and refresh submissions are disabled while their work is pending.
  A toggle re-queries Telegram and shows success only after confirming the requested outcome. Failures
  show a generic localized message and **Unknown**, never sensitive remote details, and do not retry.
  Subsequent toggles use the application version returned with the confirmed status, including after
  bot-ID backfill, without requiring a page refresh. When a successful toggle changes the application
  version, the affected application is reloaded in the background and replaces its grid row, so opening
  Edit uses its current data and version. Obsolete callbacks cannot replace rows in another workspace.
- These operations run outside the UI/session lock. Temporary one-second UI polling delivers results
  while work is pending in the active Applications tab; it never initiates Telegram queries. The previous
  polling interval is restored on completion, tab deactivation, or detach (a pre-existing faster interval
  is retained). Refreshes, workspace/bot changes, toggles, and navigation discard obsolete results and
  stop obsolete batches before their next query; an already-started remote mutation may still complete.
  Removing a webhook is remote-only: bot saves, pending setup commands, and startup re-registration
  may restore it. There is no persistent disable switch or periodic Telegram refresh.
- Role and permission screens create, edit, and delete roles, toggle role permissions, and grant or remove
  user permissions. User membership is managed from the workspace user tab.
- Successful writes display localized success notifications; service failures are displayed through the
  shared localized error handling.

## Presentation and platform constraints

The application shell provides the common navigation. Views use the Vaadin 25.2 component model and the
same localization provider as the public authentication flow. The UI is a server-side Vaadin application;
its route authorization is not a substitute for the domain service authorization rules.

## Related contracts and evidence

The authoritative domain rules are in [Identity administration](../logic/identity-administration.md).

- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/MainView.java`
- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/admin/IdentityWorkspaces.java`
- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/admin/IdentityWorkspaceDetails.java`
- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/admin/IdentityWorkspaceApplicationsTab.java`
- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/admin/TelegramWebhookIndicators.java`
- `identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/admin/TelegramWebhookIndicatorsTest.java`
- `identity-frontend-vaadin/src/test/java/vg/identity/frontend/vaadin/admin/TelegramWebhookIndicatorsUnitTest.java`
- `identity-frontend-vaadin/src/main/java/vg/identity/frontend/vaadin/config/SecurityConfiguration.java`
