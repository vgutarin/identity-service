package vg.identity.model.access;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionTest {

    @Test
    void assertUniquenessAndCorrectness_allowsCurrentPermissionSets() {
        assertThatNoException().isThrownBy(() -> Permission.assertUniquenessAndCorrectness(Permission.App.ALL));
        assertThatNoException().isThrownBy(() -> Permission.assertUniquenessAndCorrectness(Permission.Workspace.ALL));
    }

    @Test
    void assertUniquenessAndCorrectness_rejectsValuesThatAreNotLowerCase() {
        assertThatThrownBy(() -> Permission.assertUniquenessAndCorrectness(new String[]{
                "workspace.read",
                "Workspace.write"
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Permission has incorrect format: Workspace.write");
    }

    @Test
    void assertUniquenessAndCorrectness_rejectsDuplicateValues() {
        assertThatThrownBy(() -> Permission.assertUniquenessAndCorrectness(new String[]{
                "workspace.read",
                "workspace.read"
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Permissions must be unique");
    }

    @Test
    void assertUniquenessAndCorrectness_rejectsValuesWithLeadingOrTrailingSpaces() {
        assertThatThrownBy(() -> Permission.assertUniquenessAndCorrectness(new String[]{
                "workspace.read",
                " workspace.write"
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Permission has incorrect format:  workspace.write");

        assertThatThrownBy(() -> Permission.assertUniquenessAndCorrectness(new String[]{
                "workspace.read",
                "workspace.write "
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Permission has incorrect format: workspace.write ");
    }
}
