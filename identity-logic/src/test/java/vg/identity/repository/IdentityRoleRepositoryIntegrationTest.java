package vg.identity.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityRoleEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.service.IdentityWorkspaceService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "OWNER")
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
    void save_whenSameNameIsUsedInDifferentWorkspaces_returnsSavedRoles() {
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
    void save_whenWorkspaceIsMissing_throwsDataIntegrityViolationException() {
        var roleName = nextString();

        assertThatThrownBy(
                () -> roleRepository.saveAndFlush(buildRole(roleName, null))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_whenSameNameIsUsedInSameWorkspace_throwsDataIntegrityViolationException() {
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
                .build();
    }
}
