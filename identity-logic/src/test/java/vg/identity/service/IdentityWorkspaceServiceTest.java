package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

@ExtendWith(MockitoExtension.class)
class IdentityWorkspaceServiceTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    IdentityWorkspaceRepository workspaceRepository;

    @InjectMocks
    IdentityWorkspaceService service;

    @Test
    void create() {
        var workspace = IdentityWorkspaceEntity.builder()
                .name(nextString())
                .build();
        var saved = workspace(1L);

        when(workspaceRepository.saveWithNewUniqueId(workspace, uniqueIdService)).thenReturn(saved);

        assertThat(service.create(workspace)).isSameAs(saved);
        verify(workspaceRepository).flush();
    }

    @Test
    void get() {
        var workspaceId = nextLong();
        var workspace = workspace(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        assertThat(service.get(workspaceId)).isSameAs(workspace);
    }

    @Test
    void getThrows_WhenEntityIsNotFound() {
        var workspaceId = nextLong();

        assertThatThrownBy(() -> service.get(workspaceId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findAll() {
        var workspaces = List.of(workspace(1L), workspace(2L));

        when(workspaceRepository.findAll()).thenReturn(workspaces);

        assertThat(service.findAll()).isSameAs(workspaces);
    }

    @Test
    void update() {
        var workspaceId = nextLong();
        var newName = nextString();
        var model = IdentityWorkspaceEntity.builder()
                .uniqueId(workspaceId)
                .name(newName)
                .build();
        var existing = workspace(workspaceId);
        var saved = workspace(workspaceId);

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(existing)).thenReturn(saved);

        assertThat(service.update(model)).isSameAs(saved);
        assertThat(existing.getName()).isEqualTo(newName);
        verify(workspaceRepository).flush();
    }

    @Test
    void updateThrows_WhenEntityIsNotFound() {
        var model = workspace(nextLong());

        when(workspaceRepository.findById(model.getUniqueId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void updateThrows_WhenVersionIsStale() {
        var workspaceId = nextLong();
        var model = IdentityWorkspaceEntity.builder()
                .uniqueId(workspaceId)
                .version(1)
                .name(nextString())
                .build();
        var existing = IdentityWorkspaceEntity.builder()
                .uniqueId(workspaceId)
                .version(2)
                .name(nextString())
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void delete() {
        var workspaceId = nextLong();
        var workspace = workspace(workspaceId);

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        service.delete(workspaceId);

        verify(workspaceRepository).delete(workspace);
        verify(workspaceRepository).flush();
    }

    private static IdentityWorkspaceEntity workspace(long id) {
        return IdentityWorkspaceEntity.builder()
                .uniqueId(id)
                .name(nextString())
                .build();
    }
}
