package vg.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.IdentityResourceType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityUserResourcePermissionRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;

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
    @Autowired
    IdentityUserResourcePermissionRepository resourcePermissionRepository;
    @Autowired
    IdentityPermissionRepository permissionRepository;
    @Autowired
    IdentityUserSystemRoleRepository systemRoleRepository;
    @Autowired
    IdentityUserChannelRepository channelRepository;
    @Autowired
    IdentityUserRepository userRepository;
    @Autowired
    IdentityPrincipalRepository principalRepository;

    @AfterEach
    void cleanUp() {
        resourcePermissionRepository.deleteAll();
        permissionRepository.deleteAll();
        workspaceService.findAll().forEach(workspace -> workspaceService.delete(workspace.getUniqueId()));
        systemRoleRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
        principalRepository.deleteAll();
    }

    @Test
    void findByUserAndResourceType_returnsWorkspacePermissionsWithResourceAndPermissionNames() {
        var user = userService.create(IdentityUser.builder()
                .username(nextString())
                .password(nextString())
                .build());
        var workspaceName = nextString();
        var workspace = workspaceService.create(IdentityWorkspaceEntity.builder()
                .name(workspaceName)
                .build());

        authorityService.assignResourceAuthority(workspace, user, "read");

        assertThat(authorityService.findByUserAndResourceType(user, IdentityResourceType.WORKSPACE))
                .singleElement()
                .satisfies(permission -> {
                    assertThat(permission.getPrincipalUniqueId()).isEqualTo(user.getUniqueId().value());
                    assertThat(permission.getResource().getUniqueId()).isEqualTo(workspace.getUniqueId());
                    assertThat(permission.getPermissionName()).isEqualTo("read");
                    assertThat(permission.getResourceName()).isEqualTo(workspaceName);
                    assertThat(permission.getCreatedAt()).isNotNull();
                });
    }
}
