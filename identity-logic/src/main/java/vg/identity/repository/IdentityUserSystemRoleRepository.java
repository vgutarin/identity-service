package vg.identity.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import vg.identity.entity.IdentityUserSystemRoleEntity;
import vg.identity.entity.IdentityUserSystemRoleEntityId;

import java.util.List;

public interface IdentityUserSystemRoleRepository extends JpaRepository<IdentityUserSystemRoleEntity, IdentityUserSystemRoleEntityId> {
    List<IdentityUserSystemRoleEntity> getAllByIdentityPrincipalUniqueId(Long uniqueId);
}
