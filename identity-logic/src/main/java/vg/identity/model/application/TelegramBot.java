package vg.identity.model.application;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.With;
import vg.unique.id.model.UniqueId;


@Builder
public record TelegramBot(
        @JsonIgnore @With UniqueId applicationId,
        String token
) {
}
