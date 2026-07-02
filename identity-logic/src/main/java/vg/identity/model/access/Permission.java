package vg.identity.model.access;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Permission {

    public static class Workspace {
        public static final String CREATE = "workspace.create";
        public static final String READ = "workspace.read";
        public static final String UPDATE = "workspace.update";
        public static final String DELETE = "workspace.delete";

        private final static String[] SELF = {
                CREATE,
                READ,
                UPDATE,
                DELETE
        };

        static final String[] ALL = concat(SELF, App.ALL);
    }

    public static class App {
        public static final String CREATE = "app.create";
        public static final String READ = "app.read";
        public static final String UPDATE = "app.update";
        public static final String DELETE = "app.delete";

        static final String[] ALL = {
                CREATE,
                READ,
                UPDATE,
                DELETE
        };
    }

    public static class Role {
        public static final String CREATE = "role.create";
        public static final String READ = "role.read";
        public static final String UPDATE = "role.update";
        public static final String DELETE = "role.delete";

        static final String[] ALL = {
                CREATE,
                READ,
                UPDATE,
                DELETE
        };
    }

    public static final String[] ALL;

    static {
        assertUniquenessAndCorrectness(Workspace.ALL);
        assertUniquenessAndCorrectness(App.ALL);
        assertUniquenessAndCorrectness(Role.ALL);
        ALL = concat(Workspace.ALL, Role.ALL);
    }

    static void assertUniquenessAndCorrectness(String[] values) {
        var uniqueSet = Arrays.stream(values)
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toSet());

        if (uniqueSet.size() != values.length) {
            throw new IllegalArgumentException("Permissions must be unique");
        }

        for (var permission : values) {
            if (!uniqueSet.contains(permission)) {
                throw new IllegalArgumentException("Permission has incorrect format: " + permission);
            }
        }
    }

    static String[] concat(String[] ... arrays) {
        return Arrays.stream(arrays)
                .flatMap(Arrays::stream)
                .toArray(String[]::new);
    }
}
