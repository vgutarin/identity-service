package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.UserEntity;
import vg.identity.mapper.UserMapper;
import vg.identity.model.User;
import vg.identity.repository.UserRepository;
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
class UserServiceImplTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    UserRepository repository;
    @Mock
    UserMapper mapper;

    @InjectMocks
    UserServiceImpl service;

    @Test
    void create() {

        var modelToSave = User.builder().build();
        var modelSaved = model(1L);

        var entityToSave = UserEntity.builder().uniqueId(null).build();
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

        var user = User.builder().uniqueId(userId).username(updatedName).build();

        var entityId = userId.value();
        var entity = UserEntity.builder().uniqueId(entityId).build();
        var entitySaved = UserEntity.builder()
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

    @Test
    void getAll() {

        var entity1 = entity(1);
        var entity2 = entity(2);
        var entity3 = entity(3);

        var model1 = model(1);
        var model2 = model(2);
        var model3 = model(3);

        when(repository.findAll()).thenReturn(List.of(entity1, entity2, entity3));

        when(mapper.toModel(entity1)).thenReturn(model1);
        when(mapper.toModel(entity2)).thenReturn(model2);
        when(mapper.toModel(entity3)).thenReturn(model3);

        assertThat(
                service.getAll()
        ).isEqualTo(
                List.of(model1, model2, model3)
        );
    }

    private static User model(long id) {
        return User.builder().uniqueId(new UniqueId(id)).build();
    }

    private static UserEntity entity(long id) {
        return UserEntity.builder().uniqueId(id).build();
    }
}