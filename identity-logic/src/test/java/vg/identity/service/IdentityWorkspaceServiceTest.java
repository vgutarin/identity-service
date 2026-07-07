package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.entity.IdentityRoleTemplateEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityWorkspaceMapper;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityWorkspace;
import vg.identity.repository.IdentityRoleTemplateRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
class IdentityWorkspaceServiceTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    IdentityWorkspaceRepository workspaceRepository;
    @Mock
    IdentityRoleTemplateRepository roleTemplateRepository;
    @Mock
    IdentityRoleService roleService;
    @Mock
    IdentityApplicationService applicationService;
    @Mock
    IdentityUserService userService;
    @Mock
    IdentityWorkspaceMapper workspaceMapper;

    @InjectMocks
    IdentityWorkspaceService service;

    @Test
    void create_whenValidInput_returnsCreatedWorkspace() {
        var workspace = IdentityWorkspace.builder()
                .name(nextString())
                .build();
        var entityToSave = IdentityWorkspaceEntity.builder()
                .name(workspace.getName())
                .build();
        var savedEntity = workspaceEntity(1L);
        var savedModel = workspaceModel(1L);
        var templates = List.of(roleTemplateEntity(1L), roleTemplateEntity(2L));

        when(workspaceMapper.toEntity(workspace)).thenReturn(entityToSave);
        when(workspaceRepository.saveWithNewUniqueId(entityToSave, uniqueIdService)).thenReturn(savedEntity);
        when(roleTemplateRepository.findAll()).thenReturn(templates);
        when(workspaceMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.create(workspace)).isSameAs(savedModel);
        verify(workspaceRepository).flush();
        verify(roleService).createFromTemplate(templates, savedEntity);
    }

    @Test
    void getById_whenEntityExists_returnsWorkspace() {
        var workspaceId = nextLong();
        var entity = workspaceEntity(workspaceId);
        var model = workspaceModel(workspaceId);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(entity));
        when(workspaceMapper.toModel(entity)).thenReturn(model);

        assertThat(service.getById(new UniqueId(workspaceId))).isSameAs(model);
    }

    @Test
    void getById_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var workspaceId = nextUniqueId();

        assertThatThrownBy(() -> service.getById(workspaceId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAll_whenEntitiesExist_returnsWorkspaces() {
        var entities = List.of(workspaceEntity(1L), workspaceEntity(2L));
        var firstModel = workspaceModel(1L);
        var secondModel = workspaceModel(2L);

        when(workspaceRepository.findAll()).thenReturn(entities);
        when(workspaceMapper.toModel(entities.get(0))).thenReturn(firstModel);
        when(workspaceMapper.toModel(entities.get(1))).thenReturn(secondModel);

        assertThat(service.getAll()).containsExactly(firstModel, secondModel);
    }

    @Test
    void update_whenEntityExistsAndVersionMatches_returnsUpdatedWorkspace() {
        var workspaceId = nextLong();
        var newName = nextString();
        var model = IdentityWorkspace.builder()
                .uniqueId(new UniqueId(workspaceId))
                .name(newName)
                .build();
        var existing = workspaceEntity(workspaceId);
        var savedEntity = workspaceEntity(workspaceId);
        var savedModel = workspaceModel(workspaceId);

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(existing)).thenReturn(savedEntity);
        when(workspaceMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.update(model)).isSameAs(savedModel);
        verify(workspaceMapper).updateEntity(existing, model);
        verify(workspaceRepository).flush();
    }

    @Test
    void update_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var model = workspaceModel(nextLong());

        when(workspaceRepository.findById(model.getUniqueId().getLongValue())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_whenVersionIsStale_throwsObjectOptimisticLockingFailureException() {
        var workspaceId = nextLong();
        var model = IdentityWorkspace.builder()
                .uniqueId(new UniqueId(workspaceId))
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
    void delete_whenEntityExists_deleteWorkspace() {
        var workspaceId = nextLong();
        var workspace = workspaceEntity(workspaceId);

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        service.delete(new UniqueId(workspaceId));

        verify(workspaceRepository).delete(workspace);
        verify(workspaceRepository).flush();
    }

    @Test
    void createRole_whenWorkspaceExists_createRoleInWorkspace() {
        var workspaceId = nextLong();
        var workspace = workspaceEntity(workspaceId);
        var role = IdentityRole.builder()
                .name(nextString())
                .description(nextString())
                .build();
        var savedRole = IdentityRole.builder()
                .id(nextLong())
                .workspaceUniqueId(workspaceId)
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(roleService.create(role.getName(), role.getDescription(), workspace)).thenReturn(savedRole);

        assertThat(service.createRole(new UniqueId(workspaceId), role)).isSameAs(savedRole);
    }

    @Test
    void createRole_whenWorkspaceIsNotFound_throwsEntityNotFoundException() {
        var workspaceId = nextLong();
        var role = IdentityRole.builder().name(nextString()).build();

        assertThatThrownBy(() -> service.createRole(new UniqueId(workspaceId), role))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void createApplication_whenWorkspaceExists_createApplicationInWorkspace() {
        var workspaceId = nextLong();
        var workspace = workspaceEntity(workspaceId);
        var application = IdentityApplication.builder()
                .name(nextString())
                .uri(nextString())
                .data(nextString())
                .build();
        var savedApplication = IdentityApplication.builder()
                .uniqueId(new UniqueId(nextLong()))
                .workspaceUniqueId(workspaceId)
                .build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(applicationService.create(application.getName(), application.getUri(), application.getData(), workspace)).thenReturn(savedApplication);

        assertThat(service.createApplication(new UniqueId(workspaceId), application)).isSameAs(savedApplication);
    }

    @Test
    void createApplication_whenWorkspaceIsNotFound_throwsEntityNotFoundException() {
        var workspaceId = nextLong();
        var application = IdentityApplication.builder().name(nextString()).uri(nextString()).build();

        assertThatThrownBy(() -> service.createApplication(new UniqueId(workspaceId), application))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void addUser_whenWorkspaceExists_addsExistingOrCreatedUserToWorkspace() {
        var workspaceId = nextLong();
        var email = "john@example.com";
        var workspace = workspaceEntity(workspaceId);
        var user = IdentityUserEntity.builder()
                .uniqueId(nextLong())
                .build();
        var savedWorkspace = workspaceEntity(workspaceId);
        var model = workspaceModel(workspaceId);

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(userService.getOrCreateEntityByUsername(email)).thenReturn(user);
        when(workspaceRepository.save(workspace)).thenReturn(savedWorkspace);
        when(workspaceMapper.toModel(savedWorkspace)).thenReturn(model);

        assertThat(service.addUser(new UniqueId(workspaceId), email)).isSameAs(model);
        assertThat(workspace.getUsers()).contains(user);
        assertThat(user.getWorkspaces()).contains(workspace);
        verify(workspaceRepository).flush();
    }

    @Test
    void addUser_whenWorkspaceIsNotFound_throwsEntityNotFoundException() {
        var workspaceId = nextLong();

        assertThatThrownBy(() -> service.addUser(new UniqueId(workspaceId), "john@example.com"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static IdentityWorkspaceEntity workspaceEntity(long id) {
        return IdentityWorkspaceEntity.builder()
                .uniqueId(id)
                .name(nextString())
                .build();
    }

    private static IdentityWorkspace workspaceModel(long id) {
        return IdentityWorkspace.builder()
                .uniqueId(new UniqueId(id))
                .name(nextString())
                .build();
    }

    private static IdentityRoleTemplateEntity roleTemplateEntity(long id) {
        return IdentityRoleTemplateEntity.builder()
                .id(id)
                .name(nextString())
                .build();
    }
}
