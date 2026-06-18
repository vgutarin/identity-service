package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityRoleAssignmentEntity;
import vg.identity.entity.IdentityRoleAssignmentEntityId;
import vg.identity.entity.IdentityRoleEntity;

import java.util.Collection;
import java.util.List;

public interface IdentityRoleAssignmentRepository extends JpaRepository<IdentityRoleAssignmentEntity, IdentityRoleAssignmentEntityId> {
    List<IdentityRoleAssignmentEntity> findAllByPrincipal(IdentityPrincipalEntity principal);

    List<IdentityRoleAssignmentEntity> findAllByRole(IdentityRoleEntity role);

    @Query("""
        SELECT CASE WHEN COUNT(i) > 0 THEN TRUE ELSE FALSE END
        FROM IdentityRoleAssignmentEntity i
        JOIN i.role.permissions p
        WHERE i.principal.uniqueId = :principalUniqueId
        AND i.resourceUniqueId IN :accessScopes
        AND p.name = :permissionName
        """)
    boolean hasPermission(long principalUniqueId, Collection<Long> accessScopes, String permissionName);
}
