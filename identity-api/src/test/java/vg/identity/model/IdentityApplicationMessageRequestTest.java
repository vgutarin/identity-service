package vg.identity.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityApplicationMessageRequestTest {
    @Test
    void serialization_whenContentAndKeyAreProvided_usesNestedCompactContentAndPreservesKey() {
        var mapper = JsonMapper.builder().build();
        var key = UUID.randomUUID();
        var content = MessageContent.builder().text(" <b>Hello</b> {BOT_URI} 🙂 ").build();
        var request = IdentityApplicationMessageRequest.builder().content(content).idempotencyKey(key).build();

        var json = mapper.readTree(mapper.writeValueAsString(request));

        assertThat(json.size()).isEqualTo(2);
        assertThat(json.has("text")).isFalse();
        assertThat(json.path("content").size()).isEqualTo(1);
        assertThat(json.path("content").path("t").asText()).isEqualTo(content.text());
        assertThat(json.path("idempotencyKey").asText()).isEqualTo(key.toString());
        assertThat(mapper.treeToValue(json, IdentityApplicationMessageRequest.class)).isEqualTo(request);
    }

    @Test
    void constructor_whenContentIsOmittedOrNull_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> IdentityApplicationMessageRequest.builder().idempotencyKey(UUID.randomUUID()).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Message content is required");
        assertThatThrownBy(() -> IdentityApplicationMessageRequest.builder().content(null).idempotencyKey(UUID.randomUUID()).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Message content is required");
    }

    @Test
    void deserialization_whenContentIsMissingNullOrFlat_rejectsRequest() {
        var mapper = JsonMapper.builder().build();
        for (var json : new String[]{"{}", "{\"content\":null}", "{\"text\":\"hello\"}"}) {
            var body = (tools.jackson.databind.node.ObjectNode) mapper.readTree(json);
            body.put("idempotencyKey", UUID.randomUUID().toString());
            assertThatThrownBy(() -> mapper.treeToValue(body, IdentityApplicationMessageRequest.class))
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("Message content is required");
        }
    }

    @Test
    void deserialization_whenKeyIsOmittedOrNull_rejectsRequest() {
        var mapper = JsonMapper.builder().build();
        for (var json : new String[]{"{\"content\":{\"t\":\"hello\"}}", "{\"content\":{\"t\":\"hello\"},\"idempotencyKey\":null}"}) {
            assertThatThrownBy(() -> mapper.readValue(json, IdentityApplicationMessageRequest.class))
                    .isInstanceOf(RuntimeException.class).hasMessageContaining("Idempotency key is required");
        }
    }

    @Test
    void deserialization_whenKeyIsCanonicalOrUppercase_returnsSameUuid() {
        var mapper = JsonMapper.builder().build();
        var key = UUID.fromString("a841d4c4-1bc4-4b2f-aecf-1a4dc2ddfd92");
        for (var value : new String[]{key.toString(), key.toString().toUpperCase(Locale.ROOT)}) {
            var json = mapper.readTree("{\"content\":{\"t\":\"hello\"}}");
            ((tools.jackson.databind.node.ObjectNode) json).put("idempotencyKey", value);
            assertThat(mapper.treeToValue(json, IdentityApplicationMessageRequest.class).idempotencyKey()).isEqualTo(key);
        }
    }

    @Test
    void deserialization_whenKeyIsBase64Encoded_usesStandardUuidDeserialization() {
        var mapper = JsonMapper.builder().build();
        var json = "{\"content\":{\"t\":\"hello\"},\"idempotencyKey\":\"AAAAAAAAAAAAAAAAAAAAAA==\"}";

        var request = mapper.readValue(json, IdentityApplicationMessageRequest.class);

        assertThat(request.idempotencyKey()).isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }

    @Test
    void deserialization_whenKeyIsMalformed_rejectsRequest() {
        var mapper = JsonMapper.builder().build();
        for (var value : new String[]{"", " ", "invalid", "1-1-1-1-1"}) {
            var json = (tools.jackson.databind.node.ObjectNode) mapper.readTree("{\"content\":{\"t\":\"hello\"}}");
            json.put("idempotencyKey", value);
            assertThatThrownBy(() -> mapper.treeToValue(json, IdentityApplicationMessageRequest.class))
                    .isInstanceOf(RuntimeException.class);
        }
        for (var key : new String[]{"123", "{}", "[]"}) {
            var json = "{\"content\":{\"t\":\"hello\"},\"idempotencyKey\":" + key + "}";
            assertThatThrownBy(() -> mapper.readValue(json, IdentityApplicationMessageRequest.class)).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    void constructor_whenKeyIsOmittedOrNull_throwsIllegalArgumentException() {
        var content = MessageContent.builder().text("hello").build();
        assertThatThrownBy(() -> IdentityApplicationMessageRequest.builder().content(content).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Idempotency key is required");
        assertThatThrownBy(() -> IdentityApplicationMessageRequest.builder().content(content).idempotencyKey(null).build())
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Idempotency key is required");
    }
}
