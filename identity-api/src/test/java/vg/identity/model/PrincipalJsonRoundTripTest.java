package vg.identity.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import vg.unique.id.model.UniqueId;

import java.net.URI;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T004 (Phase 2 confirmation): the retyped principals carry {@link UniqueId} and {@link URI} fields and
 * must round-trip over JSON as plain <b>strings</b> with no custom serializer — {@code UniqueId} already
 * declares {@code @JsonValue} on {@code toString()} and {@code @JsonCreator(DELEGATING)} on
 * {@code parse(String)}, and Jackson handles {@code URI} as a string natively.
 */
class PrincipalJsonRoundTripTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void applicationUserPrincipal_serializesIdentifierAsStringAndRoundTrips() {
        var original = IdentityApplicationUserPrincipal.builder()
                .userUniqueId(new UniqueId(42L))
                .name("Ada Lovelace")
                .permissions(Set.of("orders.read"))
                .consentGiven(true)
                .build();

        var json = mapper.writeValueAsString(original);

        // userUniqueId is a JSON string (the encoded form), never a nested object.
        assertThat(json).contains("\"userUniqueId\":\"" + new UniqueId(42L) + "\"");
        assertThat(json).doesNotContain("longValue");

        var restored = mapper.readValue(json, IdentityApplicationUserPrincipal.class);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void applicationUserPrincipal_preConsentSerializesNullIdentifier() {
        var provisional = IdentityApplicationUserPrincipal.builder()
                .name("Ada Lovelace")
                .consentGiven(false)
                .build();

        var json = mapper.writeValueAsString(provisional);
        assertThat(json).contains("\"userUniqueId\":null");

        var restored = mapper.readValue(json, IdentityApplicationUserPrincipal.class);
        assertThat(restored.userUniqueId()).isNull();
        assertThat(restored.consentGiven()).isFalse();
        assertThat(restored.permissions()).isEmpty();
    }

    @Test
    void applicationPrincipal_serializesUniqueIdsAndUriAsStringsAndRoundTrips() {
        var original = new IdentityApplicationPrincipal(
                new UniqueId(7L),
                new UniqueId(3L),
                "Orders Bot",
                URI.create("https://t.me/orders_bot"));

        var json = mapper.writeValueAsString(original);

        assertThat(json).contains("\"applicationUniqueId\":\"" + new UniqueId(7L) + "\"");
        assertThat(json).contains("\"workspaceUniqueId\":\"" + new UniqueId(3L) + "\"");
        assertThat(json).contains("\"uri\":\"https://t.me/orders_bot\"");
        assertThat(json).doesNotContain("longValue");

        var restored = mapper.readValue(json, IdentityApplicationPrincipal.class);
        assertThat(restored).isEqualTo(original);
    }
}
