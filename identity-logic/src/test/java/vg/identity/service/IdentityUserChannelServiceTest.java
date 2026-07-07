package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.mapper.IdentityUserChannelMapper;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.user.channel.IdentityUserChannelEmail;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

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

    @Mock
    private UniqueIdService uniqueIdService;
    @Mock
    private IdentityUserChannelRepository identityChannelRepository;
    @Mock
    private IdentityUserChannelMapper mapper;
    @Mock
    private EncryptionService encryptionService;
    @Mock
    private IdentityUserChannelVerificationService channelVerificationService;

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
                .data("{}")
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
        assertThat(capturedEntity.getData()).isNull();
        verify(channelVerificationService).verify(result);
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
}
