package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.unique.id.model.UniqueId;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;

class AuthorityCheckerIntegrationTest extends BaseIntegrationTest {
    private static final String USERNAME = "authority-checker-user";

    @Autowired
    AuthorityChecker authorityChecker;

    @Test
    @WithMockUser(username = USERNAME, roles = "OWNER")
    void hasAuthority_whenUserIsOwner_returnsTrue() {
        assertThat(authorityChecker.hasAuthority("anything")).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "OWNER")
    void hasAuthority_withScopeAndUserIsOwner_returnsTrue() {
        assertThat(authorityChecker.hasAuthority(new UniqueId(Long.MAX_VALUE), "anything")).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withWorkspaceScopeAndUserHasAssignedRolePermissionOnWorkspace_returnsTrue() {
        var user = createIdentityUser(USERNAME);
        var workspace = createWorkspace();
        var permissionName = permissionName();
        var role = createRole(workspace, permissionName);
        assignRole(user, workspace.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(new UniqueId(workspace.getUniqueId()), permissionName)).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withApplicationScopeAndUserHasAssignedRolePermissionOnWorkspace_returnsTrue() {
        var user = createIdentityUser(USERNAME);
        var workspace = createWorkspace();
        var application = createApplication(workspace);
        var permissionName = permissionName();
        var role = createRole(workspace, permissionName);
        assignRole(user, workspace.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(new UniqueId(application.getUniqueId()), permissionName)).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withApplicationScopeAndUserHasAssignedRolePermissionOnApplication_returnsTrue() {
        var user = createIdentityUser(USERNAME);
        var workspace = createWorkspace();
        var application = createApplication(workspace);
        var permissionName = permissionName();
        var role = createRole(workspace, permissionName);
        assignRole(user, application.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(new UniqueId(application.getUniqueId()), permissionName)).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withWorkspacePermissionAssignedToOneWorkspace_returnsTrueOnlyForAssignedWorkspaceScope() {
        var user = createIdentityUser(USERNAME);
        var allowedWorkspace = createWorkspace();
        var allowedApplication = createApplication(allowedWorkspace);
        var forbiddenWorkspace = createWorkspace();
        var forbiddenApplication = createApplication(forbiddenWorkspace);
        var permissionName = permissionName();
        var role = createRole(allowedWorkspace, permissionName);
        assignRole(user, allowedWorkspace.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(new UniqueId(allowedWorkspace.getUniqueId()), permissionName)).isTrue();
        assertThat(authorityChecker.hasAuthority(new UniqueId(allowedApplication.getUniqueId()), permissionName)).isTrue();
        assertThat(authorityChecker.hasAuthority(new UniqueId(forbiddenWorkspace.getUniqueId()), permissionName)).isFalse();
        assertThat(authorityChecker.hasAuthority(new UniqueId(forbiddenApplication.getUniqueId()), permissionName)).isFalse();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withApplicationPermissionAssignedToOneApplication_returnsTrueOnlyForAssignedApplicationScope() {
        var user = createIdentityUser(USERNAME);
        var workspace = createWorkspace();
        var allowedApplication = createApplication(workspace);
        var forbiddenApplication = createApplication(workspace);
        var permissionName = permissionName();
        var role = createRole(workspace, permissionName);
        assignRole(user, allowedApplication.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(new UniqueId(allowedApplication.getUniqueId()), permissionName)).isTrue();
        assertThat(authorityChecker.hasAuthority(new UniqueId(forbiddenApplication.getUniqueId()), permissionName)).isFalse();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withScopeAndPermissionIsNotAssigned_returnsFalse() {
        createIdentityUser(USERNAME);
        var workspace = createWorkspace();

        assertThat(authorityChecker.hasAuthority(new UniqueId(workspace.getUniqueId()), permissionName())).isFalse();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withScopeAndResourceIsUnknown_returnsFalse() {
        createIdentityUser(USERNAME);

        assertThat(authorityChecker.hasAuthority(new UniqueId(Long.MAX_VALUE), permissionName())).isFalse();
    }

    private static String permissionName() {
        return "permission." + nextString().toLowerCase(Locale.ROOT);
    }
}
