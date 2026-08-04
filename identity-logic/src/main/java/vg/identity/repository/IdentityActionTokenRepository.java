package vg.identity.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import vg.identity.entity.IdentityActionTokenEntity;
import vg.identity.model.IdentityActionType;

import java.time.Instant;
import java.util.Optional;

public interface IdentityActionTokenRepository extends JpaRepository<IdentityActionTokenEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from IdentityActionTokenEntity token where token.id = ?1")
    Optional<IdentityActionTokenEntity> findByIdForUpdate(Long id);

    boolean existsByActionTypeAndIdentityUserChannelUniqueIdAndCreatedAtGreaterThanEqual(
            IdentityActionType actionType,
            Long channelUniqueId,
            Instant createdAt
    );
}
