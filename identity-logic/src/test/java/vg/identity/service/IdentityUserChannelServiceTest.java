package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.mapper.IdentityUserChannelMapper;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.TelegramUserPrincipal;
import vg.identity.model.user.channel.IdentityUserChannelEmail;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;

@ExtendWith(MockitoExtension.class)
class IdentityUserChannelServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-21T10:00:00Z");

    @Mock
    private UniqueIdService uniqueIdService;
    @Mock
    private IdentityUserChannelRepository identityChannelRepository;
    @Mock
    private IdentityUserChannelMapper mapper;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private IdentityActionTokenService actionTokenService;
    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Clock serviceClock;

    @InjectMocks
    private IdentityUserChannelService service;

    @Test
    void createEmailChannel_whenChannelDoesNotExist_createsAttachedEmailChannel() {
        var email = " John@Example.com ";
        var canonicalEmail = "john@example.com";
        var channelUserIdHash = new byte[]{1, 2, 3};
        var uniqueId = nextLong();
        var user = user(1L);

        var savedEntity = IdentityUserChannelEntity.builder()
                .uniqueId(uniqueId)
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId(canonicalEmail)
                .channelUserIdHash(channelUserIdHash)
                .identityUser(user)
                .payload("{}")
                .build();
        var model = emailChannel(uniqueId, canonicalEmail);

        when(encryptionService.canonicalize(email)).thenReturn(canonicalEmail);
        when(encryptionService.hashCaseSensitive(canonicalEmail)).thenReturn(channelUserIdHash);

        var entityCaptor = ArgumentCaptor.forClass(IdentityUserChannelEntity.class);
        when(identityChannelRepository.saveWithNewUniqueId(any(IdentityUserChannelEntity.class), eq(uniqueIdService)))
                .thenReturn(savedEntity);
        when(mapper.toEmailModel(savedEntity)).thenReturn(model);

        var result = service.createEmailChannel(email, user);

        assertThat(result).isSameAs(model);

        verify(identityChannelRepository).saveWithNewUniqueId(entityCaptor.capture(), eq(uniqueIdService));
        var capturedEntity = entityCaptor.getValue();
        assertThat(capturedEntity.getChannelType()).isEqualTo(IdentityChannelType.EMAIL);
        assertThat(capturedEntity.getChannelUserId()).isEqualTo(canonicalEmail);
        assertThat(capturedEntity.getChannelUserIdHash()).isEqualTo(channelUserIdHash);
        assertThat(capturedEntity.getIdentityUser()).isSameAs(user);
        assertThat(capturedEntity.getPayload()).isNull();
        verify(actionTokenService).confirm(result);
    }

    @Test
    void findEmailChannel_whenChannelExists_returnsEmailChannel() {
        var email = " John@Example.com ";
        var canonicalEmail = "john@example.com";
        var channelUserIdHash = new byte[]{7, 8, 9};
        var entity = IdentityUserChannelEntity.builder()
                .uniqueId(nextLong())
                .channelType(IdentityChannelType.EMAIL)
                .channelUserId(canonicalEmail)
                .channelUserIdHash(channelUserIdHash)
                .build();
        var model = emailChannel(entity.getUniqueId(), canonicalEmail);

        when(encryptionService.canonicalize(email)).thenReturn(canonicalEmail);
        when(encryptionService.hashCaseSensitive(canonicalEmail)).thenReturn(channelUserIdHash);
        when(identityChannelRepository.findByChannelTypeAndChannelUserIdHash(IdentityChannelType.EMAIL, channelUserIdHash))
                .thenReturn(Optional.of(entity));
        when(mapper.toEmailModel(entity)).thenReturn(model);

        var result = service.findEmailChannel(email);

        assertThat(result).isSameAs(model);
        assertThat(result.getEmail()).isEqualTo(canonicalEmail);
    }

    @Test
    void findEmailChannel_whenChannelDoesNotExist_returnsNull() {
        var email = " John@Example.com ";
        var canonicalEmail = "john@example.com";
        var channelUserIdHash = new byte[]{7, 8, 9};

        when(encryptionService.canonicalize(email)).thenReturn(canonicalEmail);
        when(encryptionService.hashCaseSensitive(canonicalEmail)).thenReturn(channelUserIdHash);
        when(identityChannelRepository.findByChannelTypeAndChannelUserIdHash(IdentityChannelType.EMAIL, channelUserIdHash))
                .thenReturn(Optional.empty());

        assertThat(service.findEmailChannel(email)).isNull();
        verify(identityChannelRepository, never()).saveWithNewUniqueId(any(IdentityUserChannelEntity.class), eq(uniqueIdService));
    }

    @Test
    void attachUser_whenChannelIsNotAttached_attachesUser() {
        var user = user(1L);
        var channelModel = emailChannel(10L, "john@example.com");
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(channelModel.getUniqueId().getLongValue())
                .build();

        when(identityChannelRepository.findById(channelModel.getUniqueId())).thenReturn(Optional.of(channelEntity));
        when(identityChannelRepository.save(channelEntity)).thenReturn(channelEntity);

        service.attachUser(channelModel, user);

        assertThat(channelEntity.getIdentityUser()).isSameAs(user);
        verify(identityChannelRepository).save(channelEntity);
        verify(identityChannelRepository).flush();
        verify(mapper).updateModel(channelModel, channelEntity);
    }

    @Test
    void attachUser_whenChannelIsAttachedToSameUser_doesNothing() {
        var user = user(1L);
        var channelModel = emailChannel(10L, "john@example.com");
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(channelModel.getUniqueId().getLongValue())
                .identityUser(user)
                .build();

        when(identityChannelRepository.findById(channelModel.getUniqueId())).thenReturn(Optional.of(channelEntity));

        service.attachUser(channelModel, user);

        verify(identityChannelRepository, never()).save(any(IdentityUserChannelEntity.class));
        verify(identityChannelRepository, never()).flush();
        verify(mapper, never()).updateModel(any(IdentityUserChannelEmail.class), any(IdentityUserChannelEntity.class));
    }

    @Test
    void attachUser_whenChannelIsAttachedToAnotherUser_throwsIllegalStateException() {
        var user = user(1L);
        var otherUser = user(2L);
        var channelModel = emailChannel(10L, "john@example.com");
        var channelEntity = IdentityUserChannelEntity.builder()
                .uniqueId(channelModel.getUniqueId().getLongValue())
                .identityUser(otherUser)
                .build();

        when(identityChannelRepository.findById(channelModel.getUniqueId())).thenReturn(Optional.of(channelEntity));

        assertThatThrownBy(() -> service.attachUser(channelModel, user))
                .isInstanceOf(IllegalStateException.class);

        verify(identityChannelRepository, never()).save(any(IdentityUserChannelEntity.class));
    }

    @Test
    void bindTelegramUser_whenChannelDoesNotExist_createsAttachedChannelWithSerializedPrincipal() throws Exception {
        var user = user(1L);
        var telegramUser = telegramUser(42L);
        var channelUserIdHash = new byte[]{4, 2};
        when(encryptionService.hashCaseSensitive("42")).thenReturn(channelUserIdHash);
        when(identityChannelRepository.findByChannelTypeAndChannelUserIdHash(
                IdentityChannelType.TELEGRAM_USER, channelUserIdHash
        )).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(telegramUser)).thenReturn("{\"id\":42}");
        when(serviceClock.instant()).thenReturn(NOW);

        var result = service.bindTelegramUser(telegramUser, user);

        assertThat(result).isEqualTo(IdentityUserChannelService.TelegramBindResult.SUCCESS);
        var captor = ArgumentCaptor.forClass(IdentityUserChannelEntity.class);
        verify(identityChannelRepository).saveWithNewUniqueId(captor.capture(), eq(uniqueIdService));
        var created = captor.getValue();
        assertThat(created.getChannelType()).isEqualTo(IdentityChannelType.TELEGRAM_USER);
        assertThat(created.getChannelUserId()).isEqualTo("42");
        assertThat(created.getChannelUserIdHash()).isEqualTo(channelUserIdHash);
        assertThat(created.getIdentityUser()).isSameAs(user);
        assertThat(created.getPayload()).isEqualTo("{\"id\":42}");
        assertThat(created.getVerifiedAt()).isEqualTo(NOW);
    }

    @Test
    void bindTelegramUser_whenUnattachedChannelIsNotVerified_attachesAndVerifiesIt() {
        var user = user(1L);
        var telegramUser = telegramUser(42L);
        var channelUserIdHash = new byte[]{4, 2};
        var channel = IdentityUserChannelEntity.builder().build();
        when(encryptionService.hashCaseSensitive("42")).thenReturn(channelUserIdHash);
        when(identityChannelRepository.findByChannelTypeAndChannelUserIdHash(
                IdentityChannelType.TELEGRAM_USER, channelUserIdHash
        )).thenReturn(Optional.of(channel));
        when(serviceClock.instant()).thenReturn(NOW);

        var result = service.bindTelegramUser(telegramUser, user);

        assertThat(result).isEqualTo(IdentityUserChannelService.TelegramBindResult.SUCCESS);
        assertThat(channel.getIdentityUser()).isSameAs(user);
        assertThat(channel.getVerifiedAt()).isEqualTo(NOW);
        verify(identityChannelRepository).save(channel);
        verify(identityChannelRepository).flush();
    }

    @Test
    void bindTelegramUser_whenUnattachedChannelIsAlreadyVerified_preservesVerificationTime() {
        var user = user(1L);
        var telegramUser = telegramUser(42L);
        var channelUserIdHash = new byte[]{4, 2};
        var verifiedAt = NOW.minusSeconds(60);
        var channel = IdentityUserChannelEntity.builder().verifiedAt(verifiedAt).build();
        when(encryptionService.hashCaseSensitive("42")).thenReturn(channelUserIdHash);
        when(identityChannelRepository.findByChannelTypeAndChannelUserIdHash(
                IdentityChannelType.TELEGRAM_USER, channelUserIdHash
        )).thenReturn(Optional.of(channel));

        var result = service.bindTelegramUser(telegramUser, user);

        assertThat(result).isEqualTo(IdentityUserChannelService.TelegramBindResult.SUCCESS);
        assertThat(channel.getIdentityUser()).isSameAs(user);
        assertThat(channel.getVerifiedAt()).isEqualTo(verifiedAt);
        verify(identityChannelRepository).save(channel);
    }

    @Test
    void bindTelegramUser_whenChannelBelongsToAnotherUser_doesNotReattachIt() {
        var user = user(1L);
        var otherUser = user(2L);
        var telegramUser = telegramUser(42L);
        var channelUserIdHash = new byte[]{4, 2};
        var channel = IdentityUserChannelEntity.builder().identityUser(otherUser).build();
        when(encryptionService.hashCaseSensitive("42")).thenReturn(channelUserIdHash);
        when(identityChannelRepository.findByChannelTypeAndChannelUserIdHash(
                IdentityChannelType.TELEGRAM_USER, channelUserIdHash
        )).thenReturn(Optional.of(channel));

        var result = service.bindTelegramUser(telegramUser, user);

        assertThat(result).isEqualTo(IdentityUserChannelService.TelegramBindResult.CHANNEL_ATTACHED_TO_ANOTHER_USER);
        assertThat(channel.getIdentityUser()).isSameAs(otherUser);
        verify(identityChannelRepository, never()).save(any(IdentityUserChannelEntity.class));
    }

    private static IdentityUserChannelEmail emailChannel(long uniqueId, String email) {
        var channel = new IdentityUserChannelEmail();
        channel.setUniqueId(new UniqueId(uniqueId));
        channel.setChannelType(IdentityChannelType.EMAIL);
        channel.setChannelUserId(email);
        return channel;
    }

    private static IdentityUserEntity user(long uniqueId) {
        return IdentityUserEntity.builder()
                .uniqueId(uniqueId)
                .build();
    }

    private static TelegramUserPrincipal telegramUser(long id) {
        return TelegramUserPrincipal.builder()
                .id(id)
                .firstName("John")
                .build();
    }
}
