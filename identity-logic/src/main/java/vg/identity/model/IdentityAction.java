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
}
