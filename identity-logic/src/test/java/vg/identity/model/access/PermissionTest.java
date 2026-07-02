package vg.identity.model.access;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionTest {

    @Test
    void assertUniquenessAndCorrectness_whenCurrentPermissionSetsAreValid_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> Permission.assertUniquenessAndCorrectness(Permission.App.ALL));
        assertThatNoException().isThrownBy(() -> Permission.assertUniquenessAndCorrectness(Permission.Workspace.ALL));
        assertThatNoException().isThrownBy(() -> Permission.assertUniquenessAndCorrectness(Permission.Role.ALL));
        assertThatNoException().isThrownBy(() -> Permission.assertUniquenessAndCorrectness(Permission.ALL));
    }

    @Test
    void all_whenComparedToHardcodedPermissionList_containsExpectedStablePermissionNames() {
        assertThat(Permission.ALL).containsExactly(
                "workspace.create",
                "workspace.read",
                "workspace.update",
                "workspace.delete",
                "app.create",
                "app.read",
                "app.update",
                "app.delete",
                "role.create",
                "role.read",
                "role.update",
                "role.delete"
        );
    }

    @Test
    void assertUniquenessAndCorrectness_whenValuesAreNotLowerCase_throwsIllegalStateException() {
        assertThatThrownBy(() -> Permission.assertUniquenessAndCorrectness(new String[]{
                "workspace.read",
                "Workspace.write"
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Permission has incorrect format: Workspace.write");
    }

    @Test
    void assertUniquenessAndCorrectness_whenValuesHaveDuplicates_throwsIllegalStateException() {
        assertThatThrownBy(() -> Permission.assertUniquenessAndCorrectness(new String[]{
                "workspace.read",
                "workspace.read"
        })).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Permissions must be unique");
    }

    @Test
    void assertUniquenessAndCorrectness_whenValuesHaveLeadingOrTrailingSpaces_throwsIllegalStateException() {
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
