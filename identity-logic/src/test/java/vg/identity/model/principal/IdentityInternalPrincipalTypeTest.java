package vg.identity.model.principal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityInternalPrincipalTypeTest {

    /**
     * The ordinals and names of these enum values are persisted in the database and
     * must never change. Changing them would silently corrupt existing data, so this test
     * pins every value to its expected name and ordinal.
     */
    @Test
    void values_whenEnumPersistsByOrdinal_returnsStableOrdinalsAndNames() {
        assertThat(IdentityInternalPrincipalType.USER.name()).isEqualTo("USER");
        assertThat(IdentityInternalPrincipalType.USER.ordinal()).isEqualTo(0);

        assertThat(IdentityInternalPrincipalType.APPLICATION.name()).isEqualTo("APPLICATION");
        assertThat(IdentityInternalPrincipalType.APPLICATION.ordinal()).isEqualTo(1);
    }

    /**
     * Guards against new values being inserted in the middle (which would shift ordinals) or
     * values being removed. New values must be appended at the end.
     */
    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(
                IdentityInternalPrincipalType.values()
        ).containsExactly(
                IdentityInternalPrincipalType.USER,
                IdentityInternalPrincipalType.APPLICATION
        );
    }
}
