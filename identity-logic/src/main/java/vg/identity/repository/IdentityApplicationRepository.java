package vg.identity.repository;

import vg.identity.entity.IdentityApplicationEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;

import java.util.List;
import java.util.Optional;

public interface IdentityApplicationRepository extends UniqueIdJpaRepository<IdentityApplicationEntity> {
    Optional<IdentityApplicationEntity> findByUriHash(byte[] uriHash);

    List<IdentityApplicationEntity> findByWorkspaceUniqueId(Long workspaceUniqueId);
}
