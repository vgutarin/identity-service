package vg.identity.mapper;

import org.mapstruct.Mapper;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.model.IdentityPermission;

@Mapper(componentModel = "spring")
public interface IdentityPermissionMapper {
    IdentityPermission toModel(IdentityPermissionEntity src);

    IdentityPermissionEntity toEntity(IdentityPermission src);

}
