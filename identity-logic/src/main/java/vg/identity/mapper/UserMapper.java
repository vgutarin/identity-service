package vg.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import vg.identity.entity.UserEntity;
import vg.identity.model.User;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface UserMapper {
    User toModel(UserEntity src);

    UserEntity toEntity(User src);

    void updateEntity(@MappingTarget UserEntity entity, User user);

    void updateModel(@MappingTarget User user, UserEntity entity);

}
