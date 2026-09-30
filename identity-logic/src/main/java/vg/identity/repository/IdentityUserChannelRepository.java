package vg.identity.repository;

import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.model.IdentityChannelType;
import vg.unique.id.jpa.UniqueIdJpaRepository;

import java.util.Optional;
import java.util.List;

public interface IdentityUserChannelRepository extends UniqueIdJpaRepository<IdentityUserChannelEntity> {
    Optional<IdentityUserChannelEntity> findByChannelTypeAndChannelUserIdHash(IdentityChannelType channelType, byte[] channelUserIdHash);

    boolean existsByIdentityUserUniqueIdAndChannelType(Long identityUserUniqueId, IdentityChannelType channelType);

    List<IdentityUserChannelEntity> findByIdentityUser_UniqueIdAndChannelTypeOrderByUpdatedAtDesc(
            Long identityUserUniqueId,
            IdentityChannelType channelType
    );
}
