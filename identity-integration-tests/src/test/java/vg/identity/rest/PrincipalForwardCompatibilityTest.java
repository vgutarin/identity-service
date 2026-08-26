package vg.identity.rest;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T014 / FR-016 / SC-007 (forward compatibility): a consumer reading the principal must keep working when
 * the service adds new optional fields later.
 *
 * <p>The typed {@code IdentityApplicationApiRestClient} deserializes responses with the consuming app's
 * Jackson (Jackson 3), whose default already disables {@code FAIL_ON_UNKNOWN_PROPERTIES}. This test pins
 * that behavior against the default {@link JsonMapper} — the same defaults the client's message converter
 * uses at runtime — so an additive contract change (a new claim) never breaks existing consumers. It is a
 * plain unit test (no Spring context / container) because the property under test is pure deserialization.</p>
 */
class PrincipalForwardCompatibilityTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void deserializingPrincipalWithUnknownFutureFields_ignoresThemAndKeepsWorking() {
        var responseWithFutureFields = """
                {"userUniqueId":"%s","name":"Ada","permissions":["orders.read"],"consentGiven":true,\
                "preferredLocale":"en-US","someFutureField":{"nested":123}}""".formatted(new UniqueId(84L));

        var principal = mapper.readValue(responseWithFutureFields, IdentityApplicationUserPrincipal.class);

        assertThat(principal.userUniqueId()).isEqualTo(new UniqueId(84L));
        assertThat(principal.name()).isEqualTo("Ada");
        assertThat(principal.permissions()).containsExactly("orders.read");
        assertThat(principal.consentGiven()).isTrue();
    }
}
