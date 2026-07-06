package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityResourceType;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityWorkspace;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "OWNER")
class IdentityUserAuthorityServiceIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityUserAuthorityService authorityService;
    @Autowired
    IdentityUserServiceImpl userService;
    @Autowired
    IdentityWorkspaceService workspaceService;

    @Test
    void findByUserAndResourceType_whenResourceTypeIsWorkspace_returnsPermissionsWithResourceAndPermissionNames() {
        var user = userService.create(IdentityUser.builder()
                .username(nextString())
                .password(nextString())
                .build());
        var workspaceName = nextString();
        var workspace = workspaceService.create(IdentityWorkspace.builder()
                .name(workspaceName)
                .build());

        authorityService.assignResourceAuthority(workspace, user, "read");

        assertThat(authorityService.findByUserAndResourceType(user, IdentityResourceType.WORKSPACE))
                .singleElement()
                .satisfies(permission -> {
                    assertThat(permission.getPrincipalUniqueId()).isEqualTo(user.getUniqueId().getLongValue());
                    assertThat(permission.getResource().getUniqueId()).isEqualTo(workspace.getUniqueId().getLongValue());
                    assertThat(permission.getPermissionName()).isEqualTo("read");
                    assertThat(permission.getResourceName()).isEqualTo(workspaceName);
                    assertThat(permission.getCreatedAt()).isNotNull();
                });
    }
}
