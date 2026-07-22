package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.mapper.IdentityUserMapper;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;
import static vg.test.TestHelper.nextUniqueId;

@ExtendWith(MockitoExtension.class)
class IdentityUserServiceTest {
    @Mock
    UniqueIdService uniqueIdService;
    @Mock
    IdentityPrincipalRepository principalRepository;
    @Mock
    IdentityUserRepository repository;

    @Mock
    IdentityUserMapper mapper;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    EncryptionService encryptionService;
    @Mock
    IdentityUserChannelService channelService;
    @Mock
    EmailService emailService;

    @InjectMocks
    IdentityUserService service;

    @BeforeEach
    void setUp() {
        // Username canonicalization is exercised elsewhere; here it is a pass-through so the existing
        // hashPrincipalName(username) stubs keep matching. hashUsername = hashPrincipalName(canonicalize).
        lenient().when(encryptionService.canonicalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_whenValidInput_returnsCreatedUser() {
        var username = nextString();
        var password = nextString();
        var encodedPassword = "encoded_" + password;
        var nameHash = new byte[]{1, 2, 3};

        var modelToSave = IdentityUser.builder().username(username).password(password).build();
        var modelSaved = model(1L);

        var entityToSave = IdentityUserEntity.builder().uniqueId(null).build();
        var entitySaved = entity(1L);
        var principalSaved = principal(1L);

        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);
        when(encryptionService.hashPrincipalName(username)).thenReturn(nameHash);
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
        assertThat(entitySaved.getPrincipal()).isSameAs(principalSaved);
        var principalCaptor = ArgumentCaptor.forClass(IdentityPrincipalEntity.class);
        verify(principalRepository).saveWithNewUniqueId(principalCaptor.capture(), eq(uniqueIdService));
        assertThat(principalCaptor.getValue().getName()).isEqualTo(username);
        assertThat(principalCaptor.getValue().getNameHash()).isEqualTo(nameHash);
        assertThat(principalCaptor.getValue().getDisplayName()).isEqualTo(username);
        assertThat(principalCaptor.getValue().getStatus()).isEqualTo(IdentityPrincipalStatus.ACTIVE);
        assertThat(principalCaptor.getValue().getType()).isEqualTo(IdentityPrincipalType.USER);
        verify(repository).flush();
        verify(channelService, never()).createEmailChannel(any(String.class), any(IdentityUserEntity.class));
    }

    @Test
    void create_whenUsernameIsEmail_createsAttachedEmailChannel() {
        var username = "john@example.com";
        var nameHash = new byte[]{1, 2, 3};
        var modelToSave = IdentityUser.builder().username(username).build();
        var modelSaved = model(1L);
        var entityToSave = IdentityUserEntity.builder().uniqueId(null).build();
        var entitySaved = entity(1L);
        var principalSaved = principal(1L);

        when(encryptionService.hashPrincipalName(username)).thenReturn(nameHash);
        when(mapper.toEntity(modelToSave)).thenReturn(entityToSave);
        when(principalRepository.saveWithNewUniqueId(any(IdentityPrincipalEntity.class), eq(uniqueIdService)))
                .thenReturn(principalSaved);
        when(repository.save(entityToSave)).thenReturn(entitySaved);
        when(emailService.validateEmail(username)).thenReturn(true);
        when(mapper.toModel(entitySaved)).thenReturn(modelSaved);

        assertThat(service.create(modelToSave)).isSameAs(modelSaved);

        verify(channelService).createEmailChannel(username, entitySaved);
    }

    @Test
    void update_whenEntityExistsAndVersionMatches_returnsUpdatedUser() {

        var userId = nextUniqueId();
        var updatedName = nextString();
        var nameHash = new byte[]{4, 5, 6};

        var user = IdentityUser.builder().uniqueId(userId).username(updatedName).build();

        var entityId = userId.getLongValue();
        var principal = principal(entityId);
        var entity = IdentityUserEntity.builder().uniqueId(entityId).principal(principal).build();
        var entitySaved = IdentityUserEntity.builder()
                .uniqueId(entityId)
                .principal(principal)
                .password(nextString())
                .build();

        when(repository.findById(userId)).thenReturn(Optional.of(entity));
        when(encryptionService.hashPrincipalName(updatedName)).thenReturn(nameHash);
        when(repository.save(entity)).thenReturn(entitySaved);

        assertThat(
                service.update(user)
        ).isSameAs(
                user
        );

        assertThat(principal.getName()).isEqualTo(updatedName);
        assertThat(principal.getNameHash()).isEqualTo(nameHash);
        verify(principalRepository).save(principal);
        verify(mapper).updateEntity(entity, user);
        verify(mapper).updateModel(user, entitySaved);
        verify(repository).flush();
    }

    @Test
    void update_whenEntityIsNotFound_throwsEntityNotFoundException() {

        var model = model(nextLong());

        when(repository.findById(model.getUniqueId())).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.update(model)
        ).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void findByUsername_whenUserExists_returnsUser() {
        var username = nextString();
        var hash = new byte[]{1, 2, 3};
        var entity = entity(1L);
        var model = model(1L);

        when(encryptionService.hashPrincipalName(username)).thenReturn(hash);
        when(repository.findByPrincipal_NameHash(hash)).thenReturn(Optional.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        assertThat(service.findByUsername(username)).isSameAs(model);
    }

    @Test
    void findByUsername_whenUserDoesNotExist_returnsNull() {
        var username = nextString();
        var hash = new byte[]{1, 2, 3};

        when(encryptionService.hashPrincipalName(username)).thenReturn(hash);
        when(repository.findByPrincipal_NameHash(hash)).thenReturn(Optional.empty());

        assertThat(service.findByUsername(username)).isNull();
    }

    @Test
    void getOrCreateEntityByUsername_whenUserExists_returnsUser() {
        var username = "john@example.com";
        var hash = new byte[]{1, 2, 3};
        var user = entity(1L);

        when(encryptionService.hashPrincipalName(username)).thenReturn(hash);
        when(repository.findByPrincipal_NameHash(hash)).thenReturn(Optional.of(user));

        assertThat(service.getOrCreateEntityByUsername(username)).isSameAs(user);
        verify(channelService, never()).createEmailChannel(any(String.class), any(IdentityUserEntity.class));
    }

    @Test
    void getOrCreateEntityByUsername_whenUserDoesNotExistAndUsernameIsEmail_createsUserWithAttachedEmailChannel() {
        var username = "john@example.com";
        var hash = new byte[]{1, 2, 3};
        var entityToSave = IdentityUserEntity.builder().uniqueId(null).build();
        var entitySaved = entity(1L);
        var principalSaved = principal(1L);

        when(encryptionService.hashPrincipalName(username)).thenReturn(hash);
        when(repository.findByPrincipal_NameHash(hash)).thenReturn(Optional.empty());
        when(mapper.toEntity(any(IdentityUser.class))).thenReturn(entityToSave);
        when(principalRepository.saveWithNewUniqueId(any(IdentityPrincipalEntity.class), eq(uniqueIdService)))
                .thenReturn(principalSaved);
        when(repository.save(entityToSave)).thenReturn(entitySaved);
        when(emailService.validateEmail(username)).thenReturn(true);

        assertThat(service.getOrCreateEntityByUsername(username)).isSameAs(entitySaved);
        verify(channelService).createEmailChannel(username, entitySaved);
    }

    @Test
    void create_whenUsernameContainsColon_throwsIllegalArgumentException() {
        var model = IdentityUser.builder().username("scheme://value").build();

        assertThatThrownBy(() -> service.create(model)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    private static IdentityUser model(long id) {
        return IdentityUser.builder().uniqueId(new UniqueId(id)).build();
    }

    private static IdentityUserEntity entity(long id) {
        return IdentityUserEntity.builder().uniqueId(id).build();
    }

    private static IdentityPrincipalEntity principal(long id) {
        return IdentityPrincipalEntity.builder()
                .uniqueId(id)
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.USER)
                .build();
    }

}
