package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.mapper.IdentityUserMapper;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityUserCommunicationChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    IdentityUserRepository repository;
    @Mock
    IdentityUserCommunicationChannelRepository communicationChannelRepository;
    @Mock
    IdentityUserMapper mapper;

    @InjectMocks
    IdentityUserServiceImpl service;

    @Test
    void create() {

        var modelToSave = IdentityUser.builder().build();
        var modelSaved = model(1L);

        var entityToSave = IdentityUserEntity.builder().uniqueId(null).build();
        var entitySaved = entity(1L);

        when(mapper.toEntity(modelToSave)).thenReturn(entityToSave);
        when(repository.saveWithNewUniqueId(entityToSave, uniqueIdService)).thenReturn(entitySaved);
        when(mapper.toModel(entitySaved)).thenReturn(modelSaved);

        assertThat(
                service.create(modelToSave)
        ).isSameAs(
                modelSaved
        );
    }

    @Test
    void update() {

        var userId = nextUniqueId();
        var updatedName = nextString();

        var user = IdentityUser.builder().uniqueId(userId).username(updatedName).build();

        var entityId = userId.value();
        var entity = IdentityUserEntity.builder().uniqueId(entityId).build();
        var entitySaved = IdentityUserEntity.builder()
                .uniqueId(entityId)
                .username(updatedName)
                .password(nextString())
                .build();

        when(repository.findById(userId)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entitySaved);

        assertThat(
                service.update(user)
        ).isSameAs(
                user
        );

        verify(mapper).updateEntity(entity, user);
        verify(mapper).updateModel(user, entitySaved);
    }

    @Test
    void updateThrows_WhenEntityIsNotFound() {

        var model = model(nextLong());

        when(repository.findById(model.getUniqueId())).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.update(model)
        ).isInstanceOf(EntityNotFoundException.class);
    }

    private static IdentityUser model(long id) {
        return IdentityUser.builder().uniqueId(new UniqueId(id)).build();
    }

    private static IdentityUserEntity entity(long id) {
        return IdentityUserEntity.builder().uniqueId(id).build();
    }
}