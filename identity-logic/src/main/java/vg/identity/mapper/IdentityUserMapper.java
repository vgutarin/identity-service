package vg.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.IdentityUser;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface IdentityUserMapper {
    @Mapping(target = "username", source = "principal.name")
    IdentityUser toModel(IdentityUserEntity src);

    @Mapping(target = "principal", ignore = true)
    @Mapping(target = "workspaces", ignore = true)
    IdentityUserEntity toEntity(IdentityUser src);

    @Mapping(target = "principal", ignore = true)
    @Mapping(target = "workspaces", ignore = true)
    void updateEntity(@MappingTarget IdentityUserEntity entity, IdentityUser user);

    @Mapping(target = "username", source = "principal.name")
    void updateModel(@MappingTarget IdentityUser user, IdentityUserEntity entity);
}
