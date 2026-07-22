package vg.identity.repository;


import vg.identity.entity.IdentityUserEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;

import java.util.Optional;

public interface IdentityUserRepository extends UniqueIdJpaRepository<IdentityUserEntity> {
    Optional<IdentityUserEntity> findByPrincipal_NameHash(byte[] username);
}
