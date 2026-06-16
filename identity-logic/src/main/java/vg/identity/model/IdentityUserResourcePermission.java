package vg.identity.model;

import vg.unique.id.jpa.UniqueIdEntity;

import java.time.Instant;

public interface IdentityUserResourcePermission {
    Long getPrincipalUniqueId();

    Instant getCreatedAt();

    String getPermissionName();

    String getResourceName();

    UniqueIdEntity getResource();
}
