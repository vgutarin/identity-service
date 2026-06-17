package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.mapper.IdentityPermissionMapper;
import vg.identity.model.IdentityPermission;
import vg.identity.repository.IdentityPermissionRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

@ExtendWith(MockitoExtension.class)
class IdentityPermissionServiceTest {
    @Mock
    IdentityPermissionRepository permissionRepository;
    @Mock
    IdentityPermissionMapper permissionMapper;

    @InjectMocks
    IdentityPermissionService service;

    @Test
    void create() {
        var permission = IdentityPermission.builder()
                .name(" Workspace.READ ")
                .build();
        var entityToSave = IdentityPermissionEntity.builder()
                .name(permission.getName())
                .build();
        var savedEntity = permissionEntity(1L);
        var savedModel = permissionModel(1L);

        when(permissionMapper.toEntity(permission)).thenReturn(entityToSave);
        when(permissionRepository.save(entityToSave)).thenReturn(savedEntity);
        when(permissionMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.create(permission)).isSameAs(savedModel);
        assertThat(entityToSave.getName()).isEqualTo("workspace.read");
        verify(permissionRepository).flush();
    }

    @Test
    void getById() {
        var id = nextLong();
        var entity = permissionEntity(id);
        var model = permissionModel(id);

        when(permissionRepository.findById(id)).thenReturn(Optional.of(entity));
        when(permissionMapper.toModel(entity)).thenReturn(model);

        assertThat(service.getById(id)).isSameAs(model);
    }

    @Test
    void getByIdThrows_WhenEntityIsNotFound() {
        assertThatThrownBy(() -> service.getById(nextLong()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAll() {
        var entities = List.of(permissionEntity(1L), permissionEntity(2L));
        var firstModel = permissionModel(1L);
        var secondModel = permissionModel(2L);

        when(permissionRepository.findAll()).thenReturn(entities);
        when(permissionMapper.toModel(entities.get(0))).thenReturn(firstModel);
        when(permissionMapper.toModel(entities.get(1))).thenReturn(secondModel);

        assertThat(service.getAll()).containsExactly(firstModel, secondModel);
    }

    @Test
    void getOrCreateEntityReturnsExistingPermission() {
        var entity = permissionEntity(nextLong());

        when(permissionRepository.findByName("workspace.read")).thenReturn(Optional.of(entity));

        assertThat(service.getOrCreateEntity(" Workspace.READ ")).isSameAs(entity);
    }

    @Test
    void getOrCreateEntityCreatesMissingPermission() {
        var name = "workspace.read";
        var saved = permissionEntity(nextLong());

        when(permissionRepository.findByName(name)).thenReturn(Optional.empty());
        when(permissionRepository.save(any(IdentityPermissionEntity.class))).thenReturn(saved);

        assertThat(service.getOrCreateEntity(name)).isSameAs(saved);
    }

    private static IdentityPermissionEntity permissionEntity(long id) {
        return IdentityPermissionEntity.builder()
                .id(id)
                .name(nextString())
                .build();
    }

    private static IdentityPermission permissionModel(long id) {
        return IdentityPermission.builder()
                .id(id)
                .name(nextString())
                .build();
    }
    @Test
    void normalizeTrimsAndLowercases() {
        assertThat(IdentityPermissionService.normalize(" Read "))
                .isEqualTo("read");
        assertThat(IdentityPermissionService.normalize("WORKSPACE:WRITE"))
                .isEqualTo("workspace:write");
    }
}
