package vg.identity.model.access;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Permission {
    static class App {
        static final String CREATE = "app.create";
        static final String READ = "app.read";
        static final String UPDATE = "app.update";
        static final String DELETE = "app.delete";

        static final String[] ALL = {
                CREATE,
                READ,
                UPDATE,
                DELETE
        };
    }

    static class Workspace {
        static final String CREATE = "workspace.create";
        static final String READ = "workspace.read";
        static final String UPDATE = "workspace.update";
        static final String DELETE = "workspace.delete";

        private final static String[] SELF = {
                CREATE,
                READ,
                UPDATE,
                DELETE
        };

        static final String[] ALL = concat(SELF, App.ALL);
    }

    public static final String[] ALL;

    static {
        assertUniquenessAndCorrectness(App.ALL);
        assertUniquenessAndCorrectness(Workspace.ALL);
        ALL = Workspace.ALL;
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
