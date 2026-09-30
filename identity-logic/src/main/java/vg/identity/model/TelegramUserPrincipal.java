package vg.identity.model;

import lombok.Builder;
import lombok.With;

import java.util.stream.Stream;

@Builder
@With
public record TelegramUserPrincipal(
        long id,
        Boolean bot,
        String firstName,
        String lastName,
        String username,
        String languageCode,
        Boolean premium,
        Boolean addedToAttachmentMenu,
        Boolean allowsWriteToPm,
        String photoUrl,
        /** Canonical E.164 number shared by this Telegram user with one of their bots; never login proof. */
        String phoneNumber
) {
    public String displayName() {
        return Stream.of(firstName, lastName)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((first, second) -> first + " " + second)
                .orElse(username);
    }
}
