package vg.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.IdentityUser;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface IdentityUserMapper {
    IdentityUser toModel(IdentityUserEntity src);

    IdentityUserEntity toEntity(IdentityUser src);

    void updateEntity(@MappingTarget IdentityUserEntity entity, IdentityUser user);

    void updateModel(@MappingTarget IdentityUser user, IdentityUserEntity entity);
}
