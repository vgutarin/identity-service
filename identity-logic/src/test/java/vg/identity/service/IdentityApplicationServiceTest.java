package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityApplicationMapper;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationServiceTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    IdentityApplicationRepository applicationRepository;
    @Mock
    IdentityPrincipalRepository principalRepository;
    @Mock
    IdentityApplicationMapper applicationMapper;
    @Mock
    EncryptionService encryptionService;

    @InjectMocks
    IdentityApplicationService service;

    @Test
    void create_whenValidInput_returnsCreatedApplication() {
        var name = nextString();
        var data = nextString();
        var nameHash = new byte[]{1, 2, 3};
        var workspace = workspace(nextLong());
        var principal = principal(nextLong(), name);
        var savedEntity = applicationEntity(principal.getUniqueId());
        var savedModel = applicationModel(principal.getUniqueId());
        var applicationCaptor = ArgumentCaptor.forClass(IdentityApplicationEntity.class);
        var principalCaptor = ArgumentCaptor.forClass(IdentityPrincipalEntity.class);

        when(principalRepository.saveWithNewUniqueId(any(IdentityPrincipalEntity.class), eq(uniqueIdService)))
                .thenReturn(principal);
        when(encryptionService.canonicalizeAndHash(name)).thenReturn(nameHash);
        when(applicationRepository.save(any(IdentityApplicationEntity.class))).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.create(name, data, workspace)).isSameAs(savedModel);

        verify(principalRepository).saveWithNewUniqueId(principalCaptor.capture(), eq(uniqueIdService));
        assertThat(principalCaptor.getValue().getDisplayName()).isEqualTo(name);
        assertThat(principalCaptor.getValue().getStatus()).isEqualTo(IdentityPrincipalStatus.ACTIVE);
        assertThat(principalCaptor.getValue().getType()).isEqualTo(IdentityPrincipalType.APPLICATION);

        verify(applicationRepository).save(applicationCaptor.capture());
        assertThat(applicationCaptor.getValue().getUniqueId()).isEqualTo(principal.getUniqueId());
        assertThat(applicationCaptor.getValue().getWorkspace()).isSameAs(workspace);
        assertThat(applicationCaptor.getValue().getName()).isEqualTo(name);
        assertThat(applicationCaptor.getValue().getNameHash()).isEqualTo(nameHash);
        assertThat(applicationCaptor.getValue().getData()).isEqualTo(data);
        verify(applicationRepository).flush();
    }

    @Test
    void getById_whenEntityExists_returnsApplication() {
        var id = nextLong();
        var entity = applicationEntity(id);
        var model = applicationModel(id);

        when(applicationRepository.findById(id)).thenReturn(Optional.of(entity));
        when(applicationMapper.toModel(entity)).thenReturn(model);

        assertThat(service.getById(new UniqueId(id))).isSameAs(model);
    }

    @Test
    void getById_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var id = nextLong();

        assertThatThrownBy(() -> service.getById(new UniqueId(id)))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findById_whenEntityExists_returnsApplication() {
        var id = nextLong();
        var entity = applicationEntity(id);
        var model = applicationModel(id);

        when(applicationRepository.findById(id)).thenReturn(Optional.of(entity));
        when(applicationMapper.toModel(entity)).thenReturn(model);

        assertThat(service.findById(new UniqueId(id))).isSameAs(model);
    }

    @Test
    void findById_whenEntityIsNotFound_returnsNull() {
        var id = nextLong();

        when(applicationRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(service.findById(new UniqueId(id))).isNull();
    }

    @Test
    void findByWorkspaceUniqueId_whenApplicationsExist_returnsApplications() {
        var workspaceId = nextLong();
        var entities = List.of(applicationEntity(1L), applicationEntity(2L));
        var firstModel = applicationModel(1L);
        var secondModel = applicationModel(2L);

        when(applicationRepository.findByWorkspaceUniqueId(workspaceId)).thenReturn(entities);
        when(applicationMapper.toModel(entities.get(0))).thenReturn(firstModel);
        when(applicationMapper.toModel(entities.get(1))).thenReturn(secondModel);

        assertThat(service.findByWorkspaceUniqueId(new UniqueId(workspaceId))).containsExactly(firstModel, secondModel);
    }

    @Test
    void update_whenEntityExistsAndVersionMatches_returnsUpdatedApplication() {
        var id = nextLong();
        var nameHash = new byte[]{4, 5, 6};
        var model = IdentityApplication.builder()
                .uniqueId(new UniqueId(id))
                .name(nextString())
                .data(nextString())
                .build();
        var existing = applicationEntity(id);
        var savedEntity = applicationEntity(id);
        var savedModel = applicationModel(id);

        when(applicationRepository.findById(id)).thenReturn(Optional.of(existing));
        when(encryptionService.canonicalizeAndHash(model.getName())).thenReturn(nameHash);
        when(applicationRepository.save(existing)).thenReturn(savedEntity);
        when(applicationMapper.toModel(savedEntity)).thenReturn(savedModel);

        assertThat(service.update(model)).isSameAs(savedModel);
        verify(applicationMapper).updateEntity(existing, model);
        assertThat(existing.getNameHash()).isEqualTo(nameHash);
        verify(applicationRepository).flush();
    }

    @Test
    void update_whenEntityIsNotFound_throwsEntityNotFoundException() {
        var model = applicationModel(nextLong());

        when(applicationRepository.findById(model.getUniqueId().getLongValue())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_whenVersionIsStale_throwsObjectOptimisticLockingFailureException() {
        var id = nextLong();
        var model = IdentityApplication.builder()
                .uniqueId(new UniqueId(id))
                .version(1)
                .name(nextString())
                .build();
        var existing = IdentityApplicationEntity.builder()
                .uniqueId(id)
                .version(2)
                .name(nextString())
                .build();

        when(applicationRepository.findById(id)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(model))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void delete_whenEntityExists_deleteApplication() {
        var id = nextLong();
        var entity = applicationEntity(id);

        when(applicationRepository.findById(id)).thenReturn(Optional.of(entity));

        service.delete(new UniqueId(id));

        verify(applicationRepository).delete(entity);
        verify(applicationRepository).flush();
    }

    private static IdentityApplicationEntity applicationEntity(long id) {
        return IdentityApplicationEntity.builder()
                .uniqueId(id)
                .version(0)
                .name(nextString())
                .build();
    }

    private static IdentityApplication applicationModel(long id) {
        return IdentityApplication.builder()
                .uniqueId(new UniqueId(id))
                .name(nextString())
                .build();
    }

    private static IdentityPrincipalEntity principal(long id, String name) {
        return IdentityPrincipalEntity.builder()
                .uniqueId(id)
                .displayName(name)
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.APPLICATION)
                .build();
    }

    private static IdentityWorkspaceEntity workspace(long uniqueId) {
        return IdentityWorkspaceEntity.builder()
                .uniqueId(uniqueId)
                .name(nextString())
                .build();
    }
}
