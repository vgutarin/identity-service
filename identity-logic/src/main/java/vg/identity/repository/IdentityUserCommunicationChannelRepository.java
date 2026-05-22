package vg.identity.repository;

import vg.identity.entity.IdentityUserCommunicationChannelEntity;
import vg.identity.model.CommunicationChannelType;
import vg.unique.id.jpa.UniqueIdJpaRepository;

import java.util.Optional;

public interface IdentityUserCommunicationChannelRepository extends UniqueIdJpaRepository<IdentityUserCommunicationChannelEntity> {
    Optional<IdentityUserCommunicationChannelEntity> findByChannelTypeAndChannelUserIdHash(CommunicationChannelType channelType, byte[] channelUserIdHash);
}
