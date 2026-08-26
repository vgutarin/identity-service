package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityInternalUserSystemRoleTest {

    /**
     * The ordinals and names of these enum values are persisted (e.g. in the database) and
     * must never change. Changing them would silently corrupt existing data, so this test
     * pins every value to its expected name and ordinal.
     */
    @Test
    void values_whenEnumPersistsByOrdinal_returnsStableOrdinalsAndNames() {
        assertThat(IdentityInternalUserSystemRole.OWNER.name()).isEqualTo("OWNER");
        assertThat(IdentityInternalUserSystemRole.OWNER.ordinal()).isEqualTo(0);
        assertThat(IdentityInternalUserSystemRole.VERIFIED_USER.name()).isEqualTo("VERIFIED_USER");
        assertThat(IdentityInternalUserSystemRole.VERIFIED_USER.ordinal()).isEqualTo(1);
    }

    /**
     * Guards against new values being inserted in the middle (which would shift ordinals) or
     * values being removed. New values must be appended at the end.
     */
    @Test
    void values_whenCalled_returnsExpectedOrder() {
        assertThat(
                IdentityInternalUserSystemRole.values()
        ).containsExactly(
                IdentityInternalUserSystemRole.OWNER,
                IdentityInternalUserSystemRole.VERIFIED_USER
        );
    }
}
