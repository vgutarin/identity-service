package vg.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.service.UniqueIdService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "USER")
class IdentityWorkspaceServicePermissionIntegrationTest extends BaseIntegrationTest {
    @Autowired
    IdentityWorkspaceService service;
    @Autowired
    IdentityWorkspaceRepository workspaceRepository;
    @Autowired
    UniqueIdService uniqueIdService;

    @AfterEach
    void cleanUp() {
        workspaceRepository.deleteAll();
    }

    @Test
    void createThrows_WhenUserIsNotAdmin() {
        assertThatThrownBy(() -> service.create(buildWorkspace()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(workspaceRepository.findAll()).isEmpty();
    }

    @Test
    void getThrows_WhenUserDoesNotHaveResourceAuthority() {
        var saved = saveWorkspace();

        assertThatThrownBy(() -> service.get(saved.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateThrows_WhenUserIsNotAdmin() {
        var saved = saveWorkspace();
        var newName = nextString();

        assertThatThrownBy(() -> service.update(
                IdentityWorkspaceEntity.builder()
                        .uniqueId(saved.getUniqueId())
                        .name(newName)
                        .build()
        )).isInstanceOf(AccessDeniedException.class);

        assertThat(workspaceRepository.findById(saved.getUniqueId()))
                .hasValueSatisfying(workspace -> assertThat(workspace.getName()).isEqualTo(saved.getName()));
    }

    @Test
    void deleteThrows_WhenUserIsNotAdmin() {
        var saved = saveWorkspace();

        assertThatThrownBy(() -> service.delete(saved.getUniqueId()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(workspaceRepository.findById(saved.getUniqueId())).isPresent();
    }

    private IdentityWorkspaceEntity saveWorkspace() {
        var saved = workspaceRepository.saveWithNewUniqueId(buildWorkspace(), uniqueIdService);
        workspaceRepository.flush();
        return saved;
    }

    private IdentityWorkspaceEntity buildWorkspace() {
        return IdentityWorkspaceEntity.builder()
                .name(nextString())
                .build();
    }
}
