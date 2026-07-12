package vg.identity.model;

import lombok.Builder;

import java.util.stream.Stream;

@Builder
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
        String photoUrl
) {
    public String displayName() {
        return Stream.of(firstName, lastName)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .reduce((first, second) -> first + " " + second)
                .orElse(username);
    }
}
