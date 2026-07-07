package vg.identity.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.mapper.IdentityUserChannelMapper;
import vg.identity.model.IdentityChannelType;
import vg.identity.model.IdentityUserChannel;
import vg.identity.model.user.channel.IdentityUserChannelEmail;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.function.Function;

@Slf4j
@RequiredArgsConstructor
@Service
@Validated
public class IdentityUserChannelService {

    private final UniqueIdService uniqueIdService;
    private final IdentityUserChannelRepository identityChannelRepository;
    private final IdentityUserChannelMapper mapper;
    private final EncryptionService encryptionService;
    private final IdentityUserChannelVerificationService channelVerificationService;

    @Transactional
    IdentityUserChannelEmail createEmailChannel(@NotNull @Email String email, @NotNull IdentityUserEntity user) {
        var result = mapper.toEmailModel(
                create(
                        IdentityChannelType.EMAIL,
                        encryptionService.canonicalize(email),
                        user
                )
        );
        channelVerificationService.verify(result);
        return result;
    }

    @Transactional(readOnly = true)
    IdentityUserChannelEmail findEmailChannel(@NotNull @Email String email) {
        return identityChannelRepository.findByChannelTypeAndChannelUserIdHash(
                        IdentityChannelType.EMAIL,
                        encryptionService.hashCaseSensitive(encryptionService.canonicalize(email))
                )
                .map(mapper::toEmailModel)
                .orElse(null);
    }

    void attachUser(IdentityUserChannel channel, IdentityUserEntity user) {
        var channelEntity = identityChannelRepository.findById(channel.getUniqueId())
                .orElseThrow(EntityNotFoundException::new);
        var attachedUser = channelEntity.getIdentityUser();
        if (attachedUser != null && !attachedUser.equals(user)) {
            log.error(
                    "User channel is attached to another user: channelUniqueId={}, requestedUserUniqueId={}, attachedUserUniqueId={}",
                    channel.getUniqueId(),
                    user.getUniqueId(),
                    attachedUser.getUniqueId()
            );
            throw new IllegalStateException("User channel is attached to another user");
        }

        if (attachedUser == null) {
            channelEntity.setIdentityUser(user);
            identityChannelRepository.save(channelEntity);
            identityChannelRepository.flush();
            mapper.updateModel(channel, channelEntity);
        }
    }

    private IdentityUserChannelEntity create(
            IdentityChannelType channelType,
            String channelUserId,
            IdentityUserEntity user
    ) {
        return identityChannelRepository.saveWithNewUniqueId(
                IdentityUserChannelEntity.builder()
                        .channelType(channelType)
                        .channelUserId(channelUserId)
                        .channelUserIdHash(encryptionService.hashCaseSensitive(channelUserId))
                        .identityUser(user)
                        .build(),
                uniqueIdService
        );
    }

    private <T extends IdentityUserChannel> T get(
            IdentityChannelType channelType,
            String channelUserId,
            IdentityUserEntity user,
            Function<IdentityUserChannelEntity, T> toModel
    ) {
        var channelUserIdHash = encryptionService.hashCaseSensitive(channelUserId);
        return toModel.apply(
                identityChannelRepository
                        .findByChannelTypeAndChannelUserIdHash(channelType, channelUserIdHash)
                        .orElseGet(() -> create(channelType, channelUserId, user))
        );
    }
}
