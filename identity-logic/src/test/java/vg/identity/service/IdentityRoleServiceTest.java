package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityRoleEntity;
import vg.identity.entity.IdentityRoleTemplateEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityRoleMapper;
import vg.identity.model.IdentityRole;
import vg.identity.repository.IdentityRoleRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

@ExtendWith(MockitoExtension.class)
class IdentityRoleServiceTest {
    @Mock
    IdentityRoleRepository roleRepository;
    @Mock
    IdentityPermissionService permissionService;
    @Mock
    IdentityRoleMapper roleMapper;

    @InjectMocks
    IdentityRoleService service;

    @Test
    void create_whenValidInput_returnsCreatedRole() {
        var name = nextString();
        var description = nextString();
        var savedEntity = roleEntity(1L);
        var savedModel = roleModel(1L);
        var captor = ArgumentCaptor.forClass(IdentityRoleEntity.class);

        when(roleRepository.save(any(IdentityRoleEntity.class))).thenReturn(savedEntity);
        when(roleMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.create(name, description, null)).isSameAs(savedModel);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(name);
        assertThat(captor.getValue().getDescription()).isEqualTo(description);
        assertThat(captor.getValue().getWorkspace()).isNull();
        assertThat(captor.getValue().getPermissions()).isEmpty();
        verify(roleRepository).flush();
    }

    @Test
    void create_whenWorkspaceIsProvided_returnsCreatedWorkspaceRole() {
        var workspace = workspace(nextLong());
        var name = nextString();
        var description = nextString();
        var savedEntity = roleEntity(1L);
        var savedModel = roleModel(1L);
        var captor = ArgumentCaptor.forClass(IdentityRoleEntity.class);

        when(roleRepository.save(any(IdentityRoleEntity.class))).thenReturn(savedEntity);
        when(roleMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.create(name, description, workspace)).isSameAs(savedModel);
        verify(roleRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(name);
        assertThat(captor.getValue().getDescription()).isEqualTo(description);
        assertThat(captor.getValue().getWorkspace()).isSameAs(workspace);
        assertThat(captor.getValue().getPermissions()).isEmpty();
        verify(roleRepository).flush();
    }

    @Test
    void createFromTemplate_whenTemplatesAreProvided_returnsCreatedRoles() {
        var templateId = nextLong();
        var workspaceId = nextLong();
        var workspace = workspace(workspaceId);
        var firstPermission = permission("workspace.read");
        var secondPermission = permission("app.update");
        var template = IdentityRoleTemplateEntity.builder()
                .id(templateId)
                .name(nextString())
                .description(nextString())
                .permissions(Set.of(firstPermission, secondPermission))
                .build();
        var savedModel = roleModel(nextLong());
        var captor = ArgumentCaptor.forClass(IdentityRoleEntity.class);

        when(roleRepository.save(any(IdentityRoleEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleMapper.toModel(any(IdentityRoleEntity.class))).thenReturn(savedModel);

        assertThat(service.createFromTemplate(List.of(template), workspace)).containsExactly(savedModel);

        verify(roleRepository).save(captor.capture());
        var copiedRole = captor.getValue();
        assertThat(copiedRole.getName()).isEqualTo(template.getName());
        assertThat(copiedRole.getDescription()).isEqualTo(template.getDescription());
        assertThat(copiedRole.getWorkspace()).isSameAs(workspace);
        assertThat(copiedRole.getPermissions()).containsExactlyInAnyOrder(firstPermission, secondPermission);
        verify(roleRepository).flush();
    }

    @Test
    void getById_whenEntityExists_returnsRole() {
        var id = nextLong();
        var entity = roleEntity(id);
        var model = roleModel(id);

        when(roleRepository.findById(id)).thenReturn(Optional.of(entity));
        when(roleMapper.toModel(entity)).thenReturn(model);

        assertThat(service.getById(id)).isSameAs(model);
    }

    @Test
    void getById_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var id = nextLong();

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAll_whenEntitiesExist_returnsRoles() {
        var entities = List.of(roleEntity(1L), roleEntity(2L));
        var firstModel = roleModel(1L);
        var secondModel = roleModel(2L);

        when(roleRepository.findAll()).thenReturn(entities);
        when(roleMapper.toModel(entities.get(0))).thenReturn(firstModel);
        when(roleMapper.toModel(entities.get(1))).thenReturn(secondModel);

        assertThat(service.getAll()).containsExactly(firstModel, secondModel);
    }

    @Test
    void update_whenEntityExistsAndVersionMatches_returnsUpdatedRole() {
        var id = nextLong();
        var model = IdentityRole.builder()
                .id(id)
                .description(nextString())
                .permissions(Set.of("workspace.delete"))
                .build();
        var existing = roleEntity(id);
        existing.setPermissions(new HashSet<>(Set.of(permission("workspace.read"))));
        var savedEntity = roleEntity(id);
        var savedModel = roleModel(id);

        when(roleRepository.findById(id)).thenReturn(Optional.of(existing));
        when(permissionService.getOrCreateEntity("workspace.delete")).thenReturn(permission("workspace.delete"));
        when(roleRepository.save(existing)).thenReturn(savedEntity);
        when(roleMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.update(model)).isSameAs(savedModel);
        verify(roleMapper).updateEntity(existing, model);
        assertThat(existing.getPermissions())
                .extracting(IdentityPermissionEntity::getName)
                .containsExactly("workspace.delete");
        verify(roleRepository).flush();
    }

    @Test
    void update_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var model = roleModel(nextLong());

        when(roleRepository.findById(model.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_whenVersionIsStale_throwsObjectOptimisticLockingFailureException() {
        var id = nextLong();
        var model = IdentityRole.builder()
                .id(id)
                .version(1)
                .name(nextString())
                .build();
        var existing = IdentityRoleEntity.builder()
                .id(id)
                .version(2)
                .name(nextString())
                .build();

        when(roleRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void delete_whenEntityExists_deleteRole() {
        var id = nextLong();
        var entity = roleEntity(id);

        when(roleRepository.findById(id)).thenReturn(Optional.of(entity));

        service.delete(id);

        verify(roleRepository).delete(entity);
        verify(roleRepository).flush();
    }

    @Test
    void addPermission_whenEntityExists_addsPermission() {
        var id = nextLong();
        var entity = roleEntity(id);
        var permission = permission("workspace.read");
        var savedModel = roleModel(id);

        when(roleRepository.findById(id)).thenReturn(Optional.of(entity));
        when(permissionService.getOrCreateEntity("workspace.read")).thenReturn(permission);
        when(roleRepository.save(entity)).thenReturn(entity);
        when(roleMapper.toModel(entity)).thenReturn(savedModel);

        assertThat(service.addPermission(id, "workspace.read")).isSameAs(savedModel);
        assertThat(entity.getPermissions()).contains(permission);
        verify(roleRepository).flush();
    }

    @Test
    void removePermission_whenEntityExists_removesPermission() {
        var id = nextLong();
        var entity = roleEntity(id);
        entity.setPermissions(new HashSet<>(Set.of(permission("workspace.read"))));
        var savedModel = roleModel(id);

        when(roleRepository.findById(id)).thenReturn(Optional.of(entity));
        when(roleRepository.save(entity)).thenReturn(entity);
        when(roleMapper.toModel(entity)).thenReturn(savedModel);

        assertThat(service.removePermission(id, " Workspace.READ ")).isSameAs(savedModel);
        assertThat(entity.getPermissions()).isEmpty();
        verify(roleRepository).flush();
    }

    private static IdentityRoleEntity roleEntity(long id) {
        return IdentityRoleEntity.builder()
                .id(id)
                .name(nextString())
                .permissions(new HashSet<>())
                .build();
    }

    private static IdentityRole roleModel(long id) {
        return IdentityRole.builder()
                .id(id)
                .name(nextString())
                .build();
    }

    private static IdentityPermissionEntity permission(String name) {
        return IdentityPermissionEntity.builder()
                .id(nextLong())
                .name(name)
                .build();
    }

    private static IdentityWorkspaceEntity workspace(long uniqueId) {
        return IdentityWorkspaceEntity.builder()
                .uniqueId(uniqueId)
                .name(nextString())
                .build();
    }
}
