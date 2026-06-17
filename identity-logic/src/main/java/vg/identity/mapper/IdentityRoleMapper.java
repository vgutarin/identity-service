package vg.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityRoleEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.IdentityRole;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface IdentityRoleMapper {
    @Mapping(target = "workspaceUniqueId", expression = "java(toWorkspaceUniqueId(src.getWorkspace()))")
    @Mapping(target = "permissions", expression = "java(toPermissionNames(src.getPermissions()))")
    IdentityRole toModel(IdentityRoleEntity src);

    @Mapping(target = "workspace", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    IdentityRoleEntity toEntity(IdentityRole src);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "workspace", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    void updateEntity(@MappingTarget IdentityRoleEntity entity, IdentityRole role);

    default Long toWorkspaceUniqueId(IdentityWorkspaceEntity workspace) {
        if (workspace == null) {
            return null;
        }

        return workspace.getUniqueId();
    }

    default Set<String> toPermissionNames(Set<IdentityPermissionEntity> permissions) {
        if (permissions == null) {
            return new HashSet<>();
        }

        return permissions.stream()
                .map(IdentityPermissionEntity::getName)
                .collect(Collectors.toSet());
    }
}
