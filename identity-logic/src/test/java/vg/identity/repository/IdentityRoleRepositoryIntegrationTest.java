package vg.identity.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityRoleEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.access.AccessScope;
import vg.identity.service.IdentityWorkspaceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "IDENTITY_ADMIN")
class IdentityRoleRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    IdentityRoleRepository roleRepository;
    @Autowired
    IdentityWorkspaceRepository workspaceRepository;
    @Autowired
    IdentityWorkspaceService workspaceService;

    @AfterEach
    void cleanUp() {
        roleRepository.deleteAll();
        workspaceRepository.deleteAll();
    }

    @Test
    void save_allowsSameNameInDifferentWorkspaces() {
        var roleName = nextString();
        var firstWorkspace = createWorkspace();
        var secondWorkspace = createWorkspace();

        var firstRole = roleRepository.saveAndFlush(buildRole(roleName, firstWorkspace));
        var secondRole = roleRepository.saveAndFlush(buildRole(roleName, secondWorkspace));

        assertThat(firstRole.getId()).isNotNull();
        assertThat(secondRole.getId()).isNotNull();
        assertThat(firstRole.getId()).isNotEqualTo(secondRole.getId());
        assertThat(roleRepository.findByNameAndWorkspace(roleName, firstWorkspace)).contains(firstRole);
        assertThat(roleRepository.findByNameAndWorkspace(roleName, secondWorkspace)).contains(secondRole);
    }

    @Test
    void save_allowsWithoutWorkspace() {
        var roleName = nextString();

        var firstRole = roleRepository.saveAndFlush(buildRole(roleName, null));
        assertThat(firstRole.getId()).isNotNull();
    }

    @Test
    void save_allowsSameNameWithoutWorkspaceAndInWorkspace() {
        var roleName = nextString();
        var workspace = createWorkspace();

        var globalRole = roleRepository.saveAndFlush(buildRole(roleName, null));
        var workspaceRole = roleRepository.saveAndFlush(buildRole(roleName, workspace));

        assertThat(globalRole.getId()).isNotNull();
        assertThat(workspaceRole.getId()).isNotNull();
        assertThat(globalRole.getId()).isNotEqualTo(workspaceRole.getId());
        assertThat(roleRepository.findByNameAndWorkspace(roleName, workspace)).contains(workspaceRole);
    }

    @Test
    void save_throwsWhenSameNameIsUsedWithoutWorkspace() {
        var roleName = nextString();

        roleRepository.saveAndFlush(buildRole(roleName, null));

        assertThatThrownBy(() -> roleRepository.saveAndFlush(buildRole(roleName, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_throwsWhenSameNameIsUsedInSameWorkspace() {
        var roleName = nextString();
        var workspace = createWorkspace();

        roleRepository.saveAndFlush(buildRole(roleName, workspace));

        assertThatThrownBy(() -> roleRepository.saveAndFlush(buildRole(roleName, workspace)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private IdentityWorkspaceEntity createWorkspace() {
        return workspaceService.create(
                IdentityWorkspaceEntity.builder()
                        .name(nextString())
                        .build()
        );
    }

    private IdentityRoleEntity buildRole(String name, IdentityWorkspaceEntity workspace) {
        return IdentityRoleEntity.builder()
                .name(name)
                .workspace(workspace)
                .accessScope(AccessScope.WORKSPACE)
                .build();
    }
}
