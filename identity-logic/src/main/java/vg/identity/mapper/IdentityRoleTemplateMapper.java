package vg.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityRoleTemplateEntity;
import vg.identity.model.IdentityRoleTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface IdentityRoleTemplateMapper {
    @Mapping(target = "permissions", expression = "java(toPermissionNames(src.getPermissions()))")
    IdentityRoleTemplate toModel(IdentityRoleTemplateEntity src);

    @Mapping(target = "permissions", ignore = true)
    IdentityRoleTemplateEntity toEntity(IdentityRoleTemplate src);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    void updateEntity(@MappingTarget IdentityRoleTemplateEntity entity, IdentityRoleTemplate template);

    default Set<String> toPermissionNames(Set<IdentityPermissionEntity> permissions) {
        if (permissions == null) {
            return new HashSet<>();
        }

        return permissions.stream()
                .map(IdentityPermissionEntity::getName)
                .collect(Collectors.toSet());
    }
}
