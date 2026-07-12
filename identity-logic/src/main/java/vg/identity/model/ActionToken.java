package vg.identity.model;

import lombok.Builder;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.model.application.TelegramBot;

import java.util.UUID;

public class ActionToken {

    @Builder
    public record ConfirmEmailInfo(
            UUID id,
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
