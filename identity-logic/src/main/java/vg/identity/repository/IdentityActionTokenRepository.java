package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityActionTokenEntity;
import vg.identity.model.IdentityActionType;

import java.time.Instant;
import java.util.UUID;

public interface IdentityActionTokenRepository extends JpaRepository<IdentityActionTokenEntity, UUID> {
    boolean existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
            IdentityActionType actionType,
            Long channelUniqueId,
            Instant createdAt
    );
}
