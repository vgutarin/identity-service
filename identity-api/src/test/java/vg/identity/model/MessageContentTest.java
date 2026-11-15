package vg.identity.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageContentTest {
    @Test
    void serialization_whenTextContainsMarkupWhitespaceAndPlaceholder_preservesLiteralTextUnderCompactKey() {
        var mapper = JsonMapper.builder().build();
        var content = MessageContent.builder().text(" <b>Hello</b> {BOT_URI} 🙂 \n").build();

        var json = mapper.readTree(mapper.writeValueAsString(content));

        assertThat(json.size()).isEqualTo(1);
        assertThat(json.has("text")).isFalse();
        assertThat(json.path("t").asText()).isEqualTo(content.text());
        assertThat(mapper.treeToValue(json, MessageContent.class)).isEqualTo(content);
    }

    @Test
    void constructor_whenTextIsMissingBlankOrTooLong_throwsIllegalArgumentException() {
        for (var text : new String[]{null, "", " \n\t", "a".repeat(4097)}) {
            assertThatThrownBy(() -> MessageContent.builder().text(text).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Message text must contain 1 to 4096 characters and must not be blank");
        }
    }

    @Test
    void constructor_whenTextContainsSupplementaryCharacters_countsUnicodeCodePoints() {
        assertThat(MessageContent.builder().text("🙂".repeat(4096)).build().text()).hasSize(8192);
        assertThatThrownBy(() -> MessageContent.builder().text("🙂".repeat(4097)).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Message text");
    }

    @Test
    void deserialization_whenCompactTextIsMissingNullOrInvalid_rejectsContent() {
        var mapper = JsonMapper.builder().build();
        for (var json : new String[]{"{}", "{\"t\":null}", "{\"t\":\" \"}", "{\"text\":\"hello\"}",
                "{\"t\":\"" + "a".repeat(4097) + "\"}"}) {
            assertThatThrownBy(() -> mapper.readValue(json, MessageContent.class)).isInstanceOf(RuntimeException.class);
        }
    }
}
