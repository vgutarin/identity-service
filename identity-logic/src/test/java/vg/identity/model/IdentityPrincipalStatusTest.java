package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityPrincipalStatusTest {

    /**
     * The ordinals and names of these enum values are persisted in the database and
     * must never change. Changing them would silently corrupt existing data, so this test
     * pins every value to its expected name and ordinal.
     */
    @Test
    void values_whenEnumPersistsByOrdinal_returnsStableOrdinalsAndNames() {
        assertThat(IdentityPrincipalStatus.VERIFICATION.name()).isEqualTo("VERIFICATION");
        assertThat(IdentityPrincipalStatus.VERIFICATION.ordinal()).isEqualTo(0);

        assertThat(IdentityPrincipalStatus.ACTIVE.name()).isEqualTo("ACTIVE");
        assertThat(IdentityPrincipalStatus.ACTIVE.ordinal()).isEqualTo(1);
    }

    /**
     * Guards against new values being inserted in the middle (which would shift ordinals) or
     * values being removed. New values must be appended at the end.
     */
    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(
                IdentityPrincipalStatus.values()
        ).containsExactly(
                IdentityPrincipalStatus.VERIFICATION,
                IdentityPrincipalStatus.ACTIVE
        );
    }
}
