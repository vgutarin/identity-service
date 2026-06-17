package vg.identity.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityUserSystemRoleTest {

    /**
     * The ordinals and names of these enum values are persisted (e.g. in the database) and
     * must never change. Changing them would silently corrupt existing data, so this test
     * pins every value to its expected name and ordinal.
     */
    @Test
    void ordinalsAndNamesAreNeverChanged() {
        assertThat(IdentityUserSystemRole.OWNER.name()).isEqualTo("OWNER");
        assertThat(IdentityUserSystemRole.OWNER.ordinal()).isEqualTo(0);
    }

    /**
     * Guards against new values being inserted in the middle (which would shift ordinals) or
     * values being removed. New values must be appended at the end.
     */
    @Test
    void valuesAreInExpectedOrder() {
        assertThat(
                IdentityUserSystemRole.values()
        ).containsExactly(
                IdentityUserSystemRole.OWNER
        );
    }
}
