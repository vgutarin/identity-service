package vg.identity.service;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import vg.identity.IdentityUserChannelVerificationProperties;
import vg.identity.entity.IdentityUserChannelVerificationEntity;
import vg.identity.model.EmailMessage;
import vg.identity.model.user.channel.IdentityUserChannelEmail;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserChannelVerificationRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Validated
public class IdentityUserChannelVerificationService {

    private final IdentityUserChannelVerificationRepository verificationRepository;
    private final IdentityUserChannelRepository channelRepository;
    private final IdentityCommandService commandService;
    private final IdentityUserChannelVerificationProperties properties;
    private final Clock clock;

    @Transactional
    public void verify(@NotNull IdentityUserChannelEmail channel) {
        Objects.requireNonNull(channel.getUniqueId(), "channel uniqueId is required");
        Objects.requireNonNull(channel.getEmail(), "channel email is required");

        var channelUniqueId = channel.getUniqueId().getLongValue();
        var createdAt = clock.instant();
        if (verificationRepository.existsByIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
                channelUniqueId,
                createdAt.minus(properties.getRequestCooldown())
        )) {
            return;
        }

        var id = UUID.randomUUID();
        var verification = IdentityUserChannelVerificationEntity.builder()
                .id(id)
                .identityUserChannel(channelRepository.getReferenceById(channelUniqueId))
                .createdAt(createdAt)
                .expireAt(createdAt.plus(properties.getExpiresIn()))
                .build();

        verificationRepository.save(verification);
        commandService.enqueue(
                EmailMessage.builder()
                        .to(List.of(channel.getEmail()))
                        .subject("Verify your email")
                        .body(properties.getLinkPrefix() + id)
                        .build()
        );
    }

    @Transactional
    public boolean verify(@NotNull UUID id) {
        var verification = verificationRepository.findById(id).orElse(null);
        var now = clock.instant();
        if (verification == null || !verification.getExpireAt().isAfter(now)) {
            return false;
        }

        var channel = verification.getIdentityUserChannel();
        channel.setVerifiedAt(now);
        channelRepository.save(channel);
        channelRepository.flush();
        return true;
    }
}
