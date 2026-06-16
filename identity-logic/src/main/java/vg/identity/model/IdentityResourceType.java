package vg.identity.model;

import lombok.Getter;

import static vg.identity.model.IdentityResourcePermission.DELETE;
import static vg.identity.model.IdentityResourcePermission.READ;
import static vg.identity.model.IdentityResourcePermission.WRITE;

public enum IdentityResourceType {
    WORKSPACE(
            READ, WRITE, DELETE
    );

    @Getter
    private final String[] permissions;

    IdentityResourceType(String... permissions) {
        this.permissions = permissions;
    }
}
