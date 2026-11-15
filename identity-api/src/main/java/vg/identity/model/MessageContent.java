package vg.identity.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/** Immutable plain-text content. Markup and placeholders are preserved literally. */
@Builder
public record MessageContent(@JsonProperty("t") String text) {
    public MessageContent {
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > 4096) {
            throw new IllegalArgumentException("Message text must contain 1 to 4096 characters and must not be blank");
        }
    }
}
