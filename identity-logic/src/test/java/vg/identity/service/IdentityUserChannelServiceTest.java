package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.mapper.IdentityUserChannelMapper;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityUserChannel;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

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

    @InjectMocks
    private IdentityUserChannelService service;

    @Test
    void get_whenChannelExists_returnsChannel() {
        var channelType = IdentityChannelType.EMAIL;
        var channelUserId = nextString();
        var channelUserIdHash = new byte[]{1, 2, 3};
        var entity = IdentityUserChannelEntity.builder()
                .uniqueId(nextLong())
                .channelType(channelType)
                .channelUserId(channelUserId)
                .channelUserIdHash(channelUserIdHash)
                .build();
        var model = IdentityUserChannel.builder()
                .uniqueId(new UniqueId(entity.getUniqueId()))
                .channelType(channelType)
                .channelUserId(channelUserId)
                .build();

        when(encryptionService.hashCaseSensitive(channelUserId)).thenReturn(channelUserIdHash);
        when(identityChannelRepository.findByChannelTypeAndChannelUserIdHash(channelType, channelUserIdHash))
                .thenReturn(Optional.of(entity));
        when(mapper.toModel(entity)).thenReturn(model);

        var result = service.get(channelType, channelUserId);

        assertThat(result).isSameAs(model);
        verify(identityChannelRepository).findByChannelTypeAndChannelUserIdHash(channelType, channelUserIdHash);
    }

    @Test
    void get_whenChannelDoesNotExist_createsAndReturnsChannel() {
        var channelType = IdentityChannelType.EMAIL;
        var channelUserId = nextString();
        var channelUserIdHash = new byte[]{4, 5, 6};
        var uniqueId = nextLong();

        var savedEntity = IdentityUserChannelEntity.builder()
                .uniqueId(uniqueId)
                .channelType(channelType)
                .channelUserId(channelUserId)
                .channelUserIdHash(channelUserIdHash)
                .data("{}")
                .build();
        var model = IdentityUserChannel.builder()
                .uniqueId(new UniqueId(uniqueId))
                .channelType(channelType)
                .channelUserId(channelUserId)
                .build();

        when(encryptionService.hashCaseSensitive(channelUserId)).thenReturn(channelUserIdHash);
        when(identityChannelRepository.findByChannelTypeAndChannelUserIdHash(channelType, channelUserIdHash))
                .thenReturn(Optional.empty());

        var entityCaptor = ArgumentCaptor.forClass(IdentityUserChannelEntity.class);
        when(identityChannelRepository.saveWithNewUniqueId(any(IdentityUserChannelEntity.class), eq(uniqueIdService)))
                .thenReturn(savedEntity);
        when(mapper.toModel(savedEntity)).thenReturn(model);

        var result = service.get(channelType, channelUserId);

        assertThat(result).isSameAs(model);

        verify(identityChannelRepository).saveWithNewUniqueId(entityCaptor.capture(), eq(uniqueIdService));
        var capturedEntity = entityCaptor.getValue();
        assertThat(capturedEntity.getChannelType()).isEqualTo(channelType);
        assertThat(capturedEntity.getChannelUserId()).isEqualTo(channelUserId);
        assertThat(capturedEntity.getChannelUserIdHash()).isEqualTo(channelUserIdHash);
        assertThat(capturedEntity.getData()).isEqualTo("{}");
    }
}
