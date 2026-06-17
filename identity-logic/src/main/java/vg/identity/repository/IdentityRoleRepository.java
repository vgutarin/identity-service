package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityRoleEntity;
import vg.identity.entity.IdentityWorkspaceEntity;

import java.util.Optional;

public interface IdentityRoleRepository extends JpaRepository<IdentityRoleEntity, Long> {
    Optional<IdentityRoleEntity> findByNameAndWorkspace(String name, IdentityWorkspaceEntity workspace);
}
