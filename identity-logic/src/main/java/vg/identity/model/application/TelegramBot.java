package vg.identity.model.application;

import lombok.Builder;

@Builder
public record TelegramBot(
        String token
) {
}
