package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.mapper.IdentityUserChannelMapper;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityUserChannel;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.service.UniqueIdService;

@Slf4j
@RequiredArgsConstructor
@Service
public class IdentityUserChannelService {

    private final UniqueIdService uniqueIdService;
    private final IdentityUserChannelRepository identityChannelRepository;
    private final IdentityUserChannelMapper mapper;
    private final EncryptionService encryptionService;

    //TODO permissions
    @Transactional
    public IdentityUserChannel get(IdentityChannelType channelType, String channelUserId) {
        var channelUserIdHash = encryptionService.hashCaseSensitive(channelUserId);
        var channelEntity = identityChannelRepository.findByChannelTypeAndChannelUserIdHash(channelType, channelUserIdHash)
                .orElse(null);

        if (channelEntity != null) {
            return mapper.toModel(channelEntity);
        }

        var newChannelEntity = IdentityUserChannelEntity.builder()
                .channelType(channelType)
                .channelUserId(channelUserId)
                .channelUserIdHash(channelUserIdHash)
                .data("{}")
                .build();

        return mapper.toModel(
                identityChannelRepository.saveWithNewUniqueId(newChannelEntity, uniqueIdService)
        );
    }
}
