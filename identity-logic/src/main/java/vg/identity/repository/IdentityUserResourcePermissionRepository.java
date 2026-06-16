package vg.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import vg.identity.entity.IdentityUserResourcePermissionEntity;
import vg.identity.entity.IdentityUserResourcePermissionEntityId;
import vg.identity.model.IdentityUserResourcePermission;

import java.util.List;

public interface IdentityUserResourcePermissionRepository extends JpaRepository<IdentityUserResourcePermissionEntity, IdentityUserResourcePermissionEntityId> {
    List<IdentityUserResourcePermissionEntity> getAllByUserUniqueId(Long userUniqueId);

    @Query("""
            select
                relation.userUniqueId as userUniqueId,
                relation.createdAt as createdAt,
                permission.name as permissionName,
                workspace.name as resourceName,
                workspace as resource
            from IdentityUserResourcePermissionEntity relation
                join IdentityPermissionEntity permission on permission.id = relation.permissionId
                join IdentityWorkspaceEntity workspace on workspace.uniqueId = relation.resourceUniqueId
            where relation.userUniqueId = :userUniqueId
            order by workspace.name, permission.name
            """)
    List<IdentityUserResourcePermission> findWorkspacePermissionsByUserUniqueId(Long userUniqueId);
}
