package vg.identity.model.principal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityInternalPrincipalStatusTest {

    /**
     * The ordinals and names of these enum values are persisted in the database and
     * must never change. Changing them would silently corrupt existing data, so this test
     * pins every value to its expected name and ordinal.
     */
    @Test
    void values_whenEnumPersistsByOrdinal_returnsStableOrdinalsAndNames() {
        assertThat(IdentityInternalPrincipalStatus.VERIFICATION.name()).isEqualTo("VERIFICATION");
        assertThat(IdentityInternalPrincipalStatus.VERIFICATION.ordinal()).isEqualTo(0);

        assertThat(IdentityInternalPrincipalStatus.ACTIVE.name()).isEqualTo("ACTIVE");
        assertThat(IdentityInternalPrincipalStatus.ACTIVE.ordinal()).isEqualTo(1);
    }

    /**
     * Guards against new values being inserted in the middle (which would shift ordinals) or
     * values being removed. New values must be appended at the end.
     */
    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(
                IdentityInternalPrincipalStatus.values()
        ).containsExactly(
                IdentityInternalPrincipalStatus.VERIFICATION,
                IdentityInternalPrincipalStatus.ACTIVE
        );
    }
}
