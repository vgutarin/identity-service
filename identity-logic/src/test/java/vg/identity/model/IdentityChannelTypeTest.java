package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityChannelTypeTest {

    /**
     * The ordinals and names of these enum values are persisted (e.g. in the database) and
     * must never change. Changing them would silently corrupt existing data, so this test
     * pins every value to its expected name and ordinal.
     */
    @Test
    void values_whenEnumPersistsByOrdinal_returnsStableOrdinalsAndNames() {

        assertThat(IdentityChannelType.EMAIL.name()).isEqualTo("EMAIL");
        assertThat(IdentityChannelType.EMAIL.ordinal()).isEqualTo(0);

        assertThat(IdentityChannelType.TELEGRAM_USER.name()).isEqualTo("TELEGRAM_USER");
        assertThat(IdentityChannelType.TELEGRAM_USER.ordinal()).isEqualTo(1);
    }

    /**
     * Guards against new values being inserted in the middle (which would shift ordinals) or
     * values being removed. New values must be appended at the end.
     */
    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(
                IdentityChannelType.values()
        ).containsExactly(
                IdentityChannelType.EMAIL,
                IdentityChannelType.TELEGRAM_USER
        );
    }
}
