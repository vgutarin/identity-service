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
        assertThat(IdentityChannelType.GENERIC.name()).isEqualTo("GENERIC");
        assertThat(IdentityChannelType.GENERIC.ordinal()).isEqualTo(0);

        assertThat(IdentityChannelType.TELEGRAM_USER.name()).isEqualTo("TELEGRAM_USER");
        assertThat(IdentityChannelType.TELEGRAM_USER.ordinal()).isEqualTo(1);

        assertThat(IdentityChannelType.TELEGRAM_BOT.name()).isEqualTo("TELEGRAM_BOT");
        assertThat(IdentityChannelType.TELEGRAM_BOT.ordinal()).isEqualTo(2);

        assertThat(IdentityChannelType.EMAIL.name()).isEqualTo("EMAIL");
        assertThat(IdentityChannelType.EMAIL.ordinal()).isEqualTo(3);

        assertThat(IdentityChannelType.PHONE.name()).isEqualTo("PHONE");
        assertThat(IdentityChannelType.PHONE.ordinal()).isEqualTo(4);
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
                IdentityChannelType.GENERIC,
                IdentityChannelType.TELEGRAM_USER,
                IdentityChannelType.TELEGRAM_BOT,
                IdentityChannelType.EMAIL,
                IdentityChannelType.PHONE
        );
    }
}
