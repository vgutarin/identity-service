package vg.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.IdentityWorkspace;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface IdentityWorkspaceMapper {
    IdentityWorkspace toModel(IdentityWorkspaceEntity src);

    @Mapping(target = "users", ignore = true)
    IdentityWorkspaceEntity toEntity(IdentityWorkspace src);

    @Mapping(target = "uniqueId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "users", ignore = true)
    void updateEntity(@MappingTarget IdentityWorkspaceEntity entity, IdentityWorkspace workspace);

    void updateModel(@MappingTarget IdentityWorkspace workspace, IdentityWorkspaceEntity entity);
}
