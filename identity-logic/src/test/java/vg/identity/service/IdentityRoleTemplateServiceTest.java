package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityRoleTemplateEntity;
import vg.identity.mapper.IdentityRoleTemplateMapper;
import vg.identity.model.IdentityRoleTemplate;
import vg.identity.repository.IdentityRoleTemplateRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

@ExtendWith(MockitoExtension.class)
class IdentityRoleTemplateServiceTest {
    @Mock
    IdentityRoleTemplateRepository roleTemplateRepository;
    @Mock
    IdentityPermissionService permissionService;
    @Mock
    IdentityRoleTemplateMapper roleTemplateMapper;

    @InjectMocks
    IdentityRoleTemplateService service;

    @Test
    void create_whenValidInput_returnsCreatedRoleTemplate() {
        var template = IdentityRoleTemplate.builder()
                .name(nextString())
                .permissions(Set.of(" Workspace.READ ", "app.update"))
                .build();
        var entityToSave = IdentityRoleTemplateEntity.builder()
                .name(template.getName())
                .build();
        var savedEntity = roleTemplateEntity(1L);
        var savedModel = roleTemplateModel(1L);

        when(roleTemplateMapper.toEntity(template)).thenReturn(entityToSave);
        when(permissionService.getOrCreateEntity(" Workspace.READ ")).thenReturn(permission("workspace.read"));
        when(permissionService.getOrCreateEntity("app.update")).thenReturn(permission("app.update"));
        when(roleTemplateRepository.save(entityToSave)).thenReturn(savedEntity);
        when(roleTemplateMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.create(template)).isSameAs(savedModel);
        assertThat(entityToSave.getPermissions())
                .extracting(IdentityPermissionEntity::getName)
                .containsExactlyInAnyOrder("workspace.read", "app.update");
        verify(roleTemplateRepository).flush();
    }

    @Test
    void getById_whenEntityExists_returnsRoleTemplate() {
        var id = nextLong();
        var entity = roleTemplateEntity(id);
        var model = roleTemplateModel(id);
        when(roleTemplateRepository.findById(id)).thenReturn(Optional.of(entity));
        when(roleTemplateMapper.toModel(entity)).thenReturn(model);

        assertThat(service.getById(id)).isSameAs(model);
    }

    @Test
    void getById_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var id = nextLong();

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAll_whenEntitiesExist_returnsRoleTemplates() {
        var entities = List.of(roleTemplateEntity(1L), roleTemplateEntity(2L));
        var firstModel = roleTemplateModel(1L);
        var secondModel = roleTemplateModel(2L);

        when(roleTemplateRepository.findAll()).thenReturn(entities);
        when(roleTemplateMapper.toModel(entities.get(0))).thenReturn(firstModel);
        when(roleTemplateMapper.toModel(entities.get(1))).thenReturn(secondModel);

        assertThat(service.getAll()).containsExactly(firstModel, secondModel);
    }

    @Test
    void update_whenEntityExistsAndVersionMatches_returnsUpdatedRoleTemplate() {
        var id = nextLong();
        var model = IdentityRoleTemplate.builder()
                .id(id)
                .description(nextString())
                .permissions(Set.of("workspace.delete"))
                .build();
        var existing = roleTemplateEntity(id);
        existing.setPermissions(new HashSet<>(Set.of(permission("workspace.read"))));
        var savedEntity = roleTemplateEntity(id);
        var savedModel = roleTemplateModel(id);

        when(roleTemplateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(permissionService.getOrCreateEntity("workspace.delete")).thenReturn(permission("workspace.delete"));
        when(roleTemplateRepository.save(existing)).thenReturn(savedEntity);
        when(roleTemplateMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.update(model)).isSameAs(savedModel);
        verify(roleTemplateMapper).updateEntity(existing, model);
        assertThat(existing.getPermissions())
                .extracting(IdentityPermissionEntity::getName)
                .containsExactly("workspace.delete");
        verify(roleTemplateRepository).flush();
    }

    @Test
    void update_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var model = roleTemplateModel(nextLong());

        when(roleTemplateRepository.findById(model.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_whenVersionIsStale_throwsObjectOptimisticLockingFailureException() {
        var id = nextLong();
        var model = IdentityRoleTemplate.builder()
                .id(id)
                .version(1)
                .name(nextString())
                .build();
        var existing = IdentityRoleTemplateEntity.builder()
                .id(id)
                .version(2)
                .name(nextString())
                .build();

        when(roleTemplateRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void delete_whenEntityExists_deleteRoleTemplate() {
        var id = nextLong();
        var existing = roleTemplateEntity(id);

        when(roleTemplateRepository.findById(id)).thenReturn(Optional.of(existing));

        service.delete(id);

        verify(roleTemplateRepository).delete(existing);
        verify(roleTemplateRepository).flush();
    }

    @Test
    void delete_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var id = nextLong();

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void addPermission_whenEntityExists_addsPermission() {
        var id = nextLong();
        var existing = roleTemplateEntity(id);
        var permission = permission("workspace.read");
        var savedEntity = roleTemplateEntity(id);
        var savedModel = roleTemplateModel(id);

        when(roleTemplateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(permissionService.getOrCreateEntity(" Workspace.READ ")).thenReturn(permission);
        when(roleTemplateRepository.save(existing)).thenReturn(savedEntity);
        when(roleTemplateMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.addPermission(id, " Workspace.READ ")).isSameAs(savedModel);
        assertThat(existing.getPermissions()).containsExactly(permission);
        verify(roleTemplateRepository).flush();
    }

    @Test
    void addPermission_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var id = nextLong();

        assertThatThrownBy(() -> service.addPermission(id, "workspace.read"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void removePermission_whenEntityExists_removesPermission() {
        var id = nextLong();
        var existing = roleTemplateEntity(id);
        existing.setPermissions(new HashSet<>(Set.of(permission("workspace.read"), permission("workspace.write"))));
        var savedEntity = roleTemplateEntity(id);
        var savedModel = roleTemplateModel(id);

        when(roleTemplateRepository.findById(id)).thenReturn(Optional.of(existing));
        when(roleTemplateRepository.save(existing)).thenReturn(savedEntity);
        when(roleTemplateMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.removePermission(id, " Workspace.READ ")).isSameAs(savedModel);
        assertThat(existing.getPermissions())
                .extracting(IdentityPermissionEntity::getName)
                .containsExactly("workspace.write");
        verify(roleTemplateRepository).flush();
    }

    @Test
    void removePermission_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var id = nextLong();

        assertThatThrownBy(() -> service.removePermission(id, "workspace.read"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static IdentityRoleTemplateEntity roleTemplateEntity(long id) {
        return IdentityRoleTemplateEntity.builder()
                .id(id)
                .name(nextString())
                .permissions(new HashSet<>())
                .build();
    }

    private static IdentityRoleTemplate roleTemplateModel(long id) {
        return IdentityRoleTemplate.builder()
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
}
