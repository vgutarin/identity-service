package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityCommandTypeTest {

    @Test
    void values_whenEnumPersistsByOrdinal_returnsStableOrdinalsAndNames() {
        assertThat(IdentityCommandType.SEND_EMAIL.name()).isEqualTo("SEND_EMAIL");
        assertThat(IdentityCommandType.SEND_EMAIL.ordinal()).isEqualTo(0);
        assertThat(IdentityCommandType.SET_TELEGRAM_WEBHOOK.name()).isEqualTo("SET_TELEGRAM_WEBHOOK");
        assertThat(IdentityCommandType.SET_TELEGRAM_WEBHOOK.ordinal()).isEqualTo(1);
    }

    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(IdentityCommandType.values()).containsExactly(IdentityCommandType.SEND_EMAIL, IdentityCommandType.SET_TELEGRAM_WEBHOOK);
    }
}
