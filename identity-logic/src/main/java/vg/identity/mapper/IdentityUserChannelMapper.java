package vg.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vg.identity.entity.IdentityUserChannelEntity;
import vg.identity.entity.IdentityUserEntity;
import vg.identity.model.IdentityUserChannel;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface IdentityUserChannelMapper {

    @Mapping(target = "identityUserUniqueId", expression = "java(toIdentityUserUniqueId(src.getIdentityUser()))")
    IdentityUserChannel toModel(IdentityUserChannelEntity src);

    @Mapping(target = "identityUser", ignore = true)
    IdentityUserChannelEntity toEntity(IdentityUserChannel src);

    @Mapping(target = "uniqueId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "identityUser", ignore = true)
    @Mapping(target = "channelUserId", ignore = true)
    @Mapping(target = "channelUserIdHash", ignore = true)
    @Mapping(target = "data", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(@MappingTarget IdentityUserChannelEntity entity, IdentityUserChannel channel);

    @Mapping(target = "identityUserUniqueId", expression = "java(toIdentityUserUniqueId(entity.getIdentityUser()))")
    void updateModel(@MappingTarget IdentityUserChannel model, IdentityUserChannelEntity entity);

    default Long toIdentityUserUniqueId(IdentityUserEntity user) {
        if (user == null) {
            return null;
        }
        return user.getUniqueId();
    }
}
