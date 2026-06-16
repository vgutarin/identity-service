package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vg.identity.entity.IdentityUserResourcePermissionEntity;
import vg.identity.entity.IdentityUserResourcePermissionEntityId;
import vg.identity.model.IdentityUserResourcePermission;

import java.util.List;

public interface IdentityUserResourcePermissionRepository extends JpaRepository<IdentityUserResourcePermissionEntity, IdentityUserResourcePermissionEntityId> {
    List<IdentityUserResourcePermissionEntity> getAllByPrincipalUniqueId(Long principalUniqueId);

    @Query("""
            select
                relation.principalUniqueId as principalUniqueId,
                relation.createdAt as createdAt,
                permission.name as permissionName,
                workspace.name as resourceName,
                workspace as resource
            from IdentityUserResourcePermissionEntity relation
                join IdentityPermissionEntity permission on permission.id = relation.permissionId
                join IdentityWorkspaceEntity workspace on workspace.uniqueId = relation.resourceUniqueId
            where relation.principalUniqueId = :principalUniqueId
            order by workspace.name, permission.name
            """)
    List<IdentityUserResourcePermission> findWorkspacePermissionsByPrincipalUniqueId(Long principalUniqueId);
}
