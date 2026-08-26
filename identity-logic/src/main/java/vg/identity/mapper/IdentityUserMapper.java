package vg.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.principal.IdentityInternalUser;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface IdentityUserMapper {
    @Mapping(target = "username", source = "principal.name")
    @Mapping(target = "displayName", source = "principal.displayName")
    IdentityInternalUser toModel(IdentityUserEntity src);

    @Mapping(target = "principal", ignore = true)
    IdentityUserEntity toEntity(IdentityInternalUser src);

    @Mapping(target = "principal", ignore = true)
    void updateEntity(@MappingTarget IdentityUserEntity entity, IdentityInternalUser user);

    @Mapping(target = "username", source = "principal.name")
    @Mapping(target = "displayName", source = "principal.displayName")
    void updateModel(@MappingTarget IdentityInternalUser user, IdentityUserEntity entity);
}
