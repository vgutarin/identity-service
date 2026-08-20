package vg.identity.model;

import lombok.Builder;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.model.application.TelegramBot;
import vg.unique.id.model.UniqueId;

public class IdentityAction {

    @Builder
    public record ConfirmEmailInfo(
            String actionKey,
            UniqueId userUniqueId,
            String suggestedDisplayName,
            boolean personalInformationConsentGiven
    ) {
    }

    public record BindTelegramInfo(
            Long tokenId,
            TelegramBot telegramBot,
            IdentityPrincipalEntity principal
    ) {
    }

    /**
     * Read-model for a validated password-reset action token, returned to the reset view so it can render
     * the set-password form. Carries the validated action key the view submits back to complete the reset.
     */
    public record ResetPasswordInfo(
            String actionKey
    ) {
    }
}
