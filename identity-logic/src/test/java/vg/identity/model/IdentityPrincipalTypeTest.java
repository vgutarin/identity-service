package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityPrincipalTypeTest {

    /**
     * The ordinals and names of these enum values are persisted in the database and
     * must never change. Changing them would silently corrupt existing data, so this test
     * pins every value to its expected name and ordinal.
     */
    @Test
    void values_whenEnumPersistsByOrdinal_returnsStableOrdinalsAndNames() {
        assertThat(IdentityPrincipalType.USER.name()).isEqualTo("USER");
        assertThat(IdentityPrincipalType.USER.ordinal()).isEqualTo(0);

        assertThat(IdentityPrincipalType.APPLICATION.name()).isEqualTo("APPLICATION");
        assertThat(IdentityPrincipalType.APPLICATION.ordinal()).isEqualTo(1);
    }

    /**
     * Guards against new values being inserted in the middle (which would shift ordinals) or
     * values being removed. New values must be appended at the end.
     */
    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(
                IdentityPrincipalType.values()
        ).containsExactly(
                IdentityPrincipalType.USER,
                IdentityPrincipalType.APPLICATION
        );
    }
}
