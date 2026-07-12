package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityActionTypeTest {

    @Test
    void values_whenEnumPersistsByOrdinal_returnsStableOrdinalsAndNames() {
        assertThat(IdentityActionType.CONFIRM_EMAIL.name()).isEqualTo("CONFIRM_EMAIL");
        assertThat(IdentityActionType.CONFIRM_EMAIL.ordinal()).isEqualTo(0);

        assertThat(IdentityActionType.BIND_TELEGRAM.name()).isEqualTo("BIND_TELEGRAM");
        assertThat(IdentityActionType.BIND_TELEGRAM.ordinal()).isEqualTo(1);
    }

    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(IdentityActionType.values()).containsExactly(
                IdentityActionType.CONFIRM_EMAIL,
                IdentityActionType.BIND_TELEGRAM
        );
    }
}
