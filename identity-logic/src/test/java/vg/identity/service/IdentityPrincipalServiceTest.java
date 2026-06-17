package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.mapper.IdentityUserMapper;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
class IdentityPrincipalServiceTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    IdentityPrincipalRepository principalRepository;
    @Mock
    IdentityUserRepository repository;
    @Mock
    IdentityUserChannelRepository communicationChannelRepository;
    @Mock
    IdentityUserMapper mapper;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    EncryptionService encryptionService;

    @InjectMocks
    IdentityPrincipalService service;

    @Test
    void create() {
        var username = nextString();
        var password = nextString();
        var encodedPassword = "encoded_" + password;
        var usernameHash = new byte[]{1, 2, 3};

        var modelToSave = IdentityUser.builder().username(username).password(password).build();
        var modelSaved = model(1L);

        var entityToSave = IdentityUserEntity.builder().uniqueId(null).build();
        var entitySaved = entity(1L);
        var principalSaved = IdentityPrincipalEntity.builder()
                .uniqueId(1L)
                .displayName(username)
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.USER)
                .build();

        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(encryptionService.canonicalizeAndHash(username)).thenReturn(usernameHash);
        when(mapper.toEntity(modelToSave)).thenReturn(entityToSave);
        when(principalRepository.saveWithNewUniqueId(any(IdentityPrincipalEntity.class), eq(uniqueIdService)))
                .thenReturn(principalSaved);
        when(repository.save(entityToSave)).thenReturn(entitySaved);
        when(mapper.toModel(entitySaved)).thenReturn(modelSaved);

        assertThat(
                service.create(modelToSave)
        ).isSameAs(
                modelSaved
        );

        assertThat(modelToSave.getPassword()).isEqualTo(encodedPassword);
        assertThat(entityToSave.getUniqueId()).isEqualTo(principalSaved.getUniqueId());
        assertThat(entityToSave.getUsernameHash()).isEqualTo(usernameHash);
        var principalCaptor = ArgumentCaptor.forClass(IdentityPrincipalEntity.class);
        verify(principalRepository).saveWithNewUniqueId(principalCaptor.capture(), eq(uniqueIdService));
        assertThat(principalCaptor.getValue().getDisplayName()).isEqualTo(username);
        assertThat(principalCaptor.getValue().getStatus()).isEqualTo(IdentityPrincipalStatus.ACTIVE);
        assertThat(principalCaptor.getValue().getType()).isEqualTo(IdentityPrincipalType.USER);
        verify(repository).flush();
    }

    @Test
    void update() {

        var userId = nextUniqueId();
        var updatedName = nextString();
        var usernameHash = new byte[]{4, 5, 6};

        var user = IdentityUser.builder().uniqueId(userId).username(updatedName).build();

        var entityId = userId.value();
        var entity = IdentityUserEntity.builder().uniqueId(entityId).build();
        var entitySaved = IdentityUserEntity.builder()
                .uniqueId(entityId)
                .username(updatedName)
                .password(nextString())
                .build();

        when(repository.findById(userId)).thenReturn(Optional.of(entity));
        when(encryptionService.canonicalizeAndHash(updatedName)).thenReturn(usernameHash);
        when(repository.save(entity)).thenReturn(entitySaved);

        assertThat(
                service.update(user)
        ).isSameAs(
                user
        );

        assertThat(entity.getUsernameHash()).isEqualTo(usernameHash);
        verify(mapper).updateEntity(entity, user);
        verify(mapper).updateModel(user, entitySaved);
        verify(repository).flush();
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
    void findByUsername() {
        var username = nextString();
        var hash = new byte[]{1, 2, 3};
        var entity = entity(1L);
        var model = model(1L);

        when(encryptionService.canonicalizeAndHash(username)).thenReturn(hash);
        when(repository.findByUsernameHash(hash)).thenReturn(Optional.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        assertThat(service.findByUsername(username)).isSameAs(model);
    }

    @Test
    void findByUsername_NotFound() {
        var username = nextString();
        var hash = new byte[]{1, 2, 3};

        when(encryptionService.canonicalizeAndHash(username)).thenReturn(hash);
        when(repository.findByUsernameHash(hash)).thenReturn(Optional.empty());

        assertThat(service.findByUsername(username)).isNull();
    }

    @Test
    void get_existingUser() {
        var channelType = IdentityChannelType.TELEGRAM_USER;
        var channelUserId = nextString();
        var hash = new byte[]{4, 5, 6};
        var userEntity = entity(1L);
        var channelEntity = IdentityUserChannelEntity.builder()
                .identityUser(userEntity)
                .build();
        var model = model(1L);

        when(encryptionService.hashCaseSensitive(channelUserId)).thenReturn(hash);
        when(communicationChannelRepository.findByChannelTypeAndChannelUserIdHash(channelType, hash))
                .thenReturn(Optional.of(channelEntity));
        when(mapper.toModel(userEntity)).thenReturn(model);

        assertThat(service.get(channelType, channelUserId)).isSameAs(model);
    }

    @Test
    void get_newUser() {
        var channelType = IdentityChannelType.TELEGRAM_USER;
        var channelUserId = nextString();
        var channelUserIdHash = new byte[]{1};
        var usernameHash = new byte[]{2};

        when(encryptionService.hashCaseSensitive(channelUserId)).thenReturn(channelUserIdHash);
        when(communicationChannelRepository.findByChannelTypeAndChannelUserIdHash(channelType, channelUserIdHash))
                .thenReturn(Optional.empty());

        var userEntity = new IdentityUserEntity();
        var userModel = model(1L);
        var principalSaved = IdentityPrincipalEntity.builder().uniqueId(1L).build();

        when(mapper.toEntity(any(IdentityUser.class))).thenReturn(userEntity);
        when(encryptionService.canonicalizeAndHash(any())).thenReturn(usernameHash);
        when(principalRepository.saveWithNewUniqueId(any(IdentityPrincipalEntity.class), eq(uniqueIdService)))
                .thenReturn(principalSaved);
        when(repository.save(userEntity)).thenReturn(userEntity);
        when(mapper.toModel(userEntity)).thenReturn(userModel);

        var channelEntityCaptor = ArgumentCaptor.forClass(IdentityUserChannelEntity.class);

        var result = service.get(channelType, channelUserId);

        assertThat(result).isSameAs(userModel);

        verify(communicationChannelRepository).saveWithNewUniqueId(channelEntityCaptor.capture(), eq(uniqueIdService));

        var savedChannel = channelEntityCaptor.getValue();
        assertThat(savedChannel.getChannelType()).isEqualTo(channelType);
        assertThat(savedChannel.getChannelUserId()).isEqualTo(channelUserId);
        assertThat(savedChannel.getChannelUserIdHash()).isEqualTo(channelUserIdHash);
        assertThat(savedChannel.getIdentityUser()).isSameAs(userEntity);
        assertThat(userEntity.getUniqueId()).isEqualTo(principalSaved.getUniqueId());
        assertThat(userEntity.getUsernameHash()).isEqualTo(usernameHash);
    }

    private static IdentityUser model(long id) {
        return IdentityUser.builder().uniqueId(new UniqueId(id)).build();
    }

    private static IdentityUserEntity entity(long id) {
        return IdentityUserEntity.builder().uniqueId(id).build();
    }
}
