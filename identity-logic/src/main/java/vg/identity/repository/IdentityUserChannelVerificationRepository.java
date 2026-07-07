package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityUserChannelVerificationEntity;

import java.time.Instant;
import java.util.UUID;

public interface IdentityUserChannelVerificationRepository extends JpaRepository<IdentityUserChannelVerificationEntity, UUID> {
    boolean existsByIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(Long channelUniqueId, Instant createdAt);
}
