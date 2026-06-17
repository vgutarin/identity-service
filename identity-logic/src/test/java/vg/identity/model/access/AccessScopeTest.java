package vg.identity.model.access;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessScopeTest {

    /**
     * The ordinals and names of these enum values are persisted in the database and
     * must never change. Changing them would silently corrupt existing data, so this test
     * pins every value to its expected name and ordinal.
     */
    @Test
    void ordinalsAndNamesAreNeverChanged() {
        assertThat(AccessScope.GLOBAL.name()).isEqualTo("GLOBAL");
        assertThat(AccessScope.GLOBAL.ordinal()).isEqualTo(0);

        assertThat(AccessScope.WORKSPACE.name()).isEqualTo("WORKSPACE");
        assertThat(AccessScope.WORKSPACE.ordinal()).isEqualTo(1);

        assertThat(AccessScope.APPLICATION.name()).isEqualTo("APPLICATION");
        assertThat(AccessScope.APPLICATION.ordinal()).isEqualTo(2);
    }

    /**
     * Guards against new values being inserted in the middle (which would shift ordinals) or
     * values being removed. New values must be appended at the end.
     */
    @Test
    void valuesAreInExpectedOrder() {
        assertThat(
                AccessScope.values()
        ).containsExactly(
                AccessScope.GLOBAL,
                AccessScope.WORKSPACE,
                AccessScope.APPLICATION
        );
    }
}
