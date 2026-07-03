package vg.identity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.IdentityApplication;
import vg.unique.id.mapper.UniqueIdMapper;

@Mapper(componentModel = "spring", uses = UniqueIdMapper.class)
public interface IdentityApplicationMapper {
    @Mapping(target = "workspaceUniqueId", expression = "java(toWorkspaceUniqueId(src.getWorkspace()))")
    IdentityApplication toModel(IdentityApplicationEntity src);

    @Mapping(target = "principal", ignore = true)
    @Mapping(target = "workspace", ignore = true)
    @Mapping(target = "uriHash", ignore = true)
    IdentityApplicationEntity toEntity(IdentityApplication src);

    @Mapping(target = "uniqueId", ignore = true)
    @Mapping(target = "principal", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "workspace", ignore = true)
    @Mapping(target = "uriHash", ignore = true)
    void updateEntity(@MappingTarget IdentityApplicationEntity entity, IdentityApplication application);

    default Long toWorkspaceUniqueId(IdentityWorkspaceEntity workspace) {
        if (workspace == null) {
            return null;
        }

        return workspace.getUniqueId();
    }
}
