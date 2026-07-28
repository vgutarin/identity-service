package vg.identity.repository;

import vg.identity.entity.IdentityRoleEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.unique.id.jpa.UniqueIdJpaRepository;

import java.util.Optional;

public interface IdentityRoleRepository extends UniqueIdJpaRepository<IdentityRoleEntity> {
    Optional<IdentityRoleEntity> findByNameAndWorkspace(String name, IdentityWorkspaceEntity workspace);
}
