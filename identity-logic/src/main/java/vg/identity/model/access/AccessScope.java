package vg.identity.model.access;

import lombok.Getter;

public enum AccessScope {
    GLOBAL(
            Permission.Workspace.ALL
    ),
    WORKSPACE(
            Permission.Workspace.ALL
    ),
    APPLICATION(
            Permission.App.ALL
    );

    @Getter
    private final String[] permissions;

    AccessScope(String... permissions) {
        this.permissions = permissions;
    }

}
