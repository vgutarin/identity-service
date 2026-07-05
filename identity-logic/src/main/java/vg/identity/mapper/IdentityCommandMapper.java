package vg.identity.mapper;

import org.mapstruct.Mapper;
import vg.identity.entity.IdentityCommandEntity;
import vg.identity.model.IdentityCommand;

@Mapper(componentModel = "spring")
public interface IdentityCommandMapper {
    IdentityCommand toModel(IdentityCommandEntity src);

    IdentityCommandEntity toEntity(IdentityCommand src);
}
