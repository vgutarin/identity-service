package vg.identity.repository;

import vg.identity.entity.IdentityPrincipalEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;

public interface IdentityPrincipalRepository extends UniqueIdJpaRepository<IdentityPrincipalEntity> {
}
