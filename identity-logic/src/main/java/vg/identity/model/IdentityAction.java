package vg.identity.model;

import lombok.Builder;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.model.application.TelegramBot;
import vg.unique.id.model.UniqueId;

import java.util.UUID;

public class IdentityAction {

    @Builder
    public record ConfirmEmailInfo(
            UUID id,
            UniqueId userUniqueId,
            boolean personalInformationConsentGiven
    ) {
    }

    public record BindTelegramInfo(
            UUID id,
            TelegramBot telegramBot,
            IdentityPrincipalEntity principal
    ) {
    }
}
