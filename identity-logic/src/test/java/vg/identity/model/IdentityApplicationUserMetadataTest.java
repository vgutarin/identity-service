package vg.identity.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApplicationUserMetadataTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jackson_serializesOnlyEnabledFlagsUsingTheCompactProperty() throws Exception {
        var metadata = new IdentityApplicationUserMetadata(new int[]{0});

        assertThat(objectMapper.writeValueAsString(metadata)).isEqualTo("{\"f\":[0]}");
        assertThat(objectMapper.writeValueAsString(new IdentityApplicationUserMetadata(null))).isEqualTo("{}");
    }

    @Test
    void constructor_normalizesFlagOrdinalsAndRepresentsAnEmptySetAsAbsent() {
        var normalized = new IdentityApplicationUserMetadata(new int[]{9, 0, 9});
        var empty = new IdentityApplicationUserMetadata(new int[0]);

        assertThat(normalized.flags()).containsExactly(0, 9);
        assertThat(empty.flags()).isNull();
    }

    @Test
    void withFlag_preservesUnknownFutureOrdinalsAndRemovesOnlyTheRequestedFlag() {
        var metadata = new IdentityApplicationUserMetadata(new int[]{0, 9});

        var revoked = metadata.withFlag(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE, false);

        assertThat(revoked.flags()).containsExactly(9);
        assertThat(revoked.hasFlag(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE)).isFalse();
    }

    @Test
    void withFlag_setsAndUnsetsTelegramPhoneShared() {
        var empty = new IdentityApplicationUserMetadata(null);

        var shared = empty.withFlag(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE, true);
        var unshared = shared.withFlag(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE, false);

        assertThat(shared.flags()).containsExactly(0);
        assertThat(shared.hasFlag(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE)).isTrue();
        assertThat(unshared.flags()).isNull();
        assertThat(unshared.hasFlag(IdentityApplicationUserMetadataFlag.TELEGRAM_PHONE_DISCLOSURE)).isFalse();
    }
}
