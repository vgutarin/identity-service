package vg.identity.model.access;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Permission {
    static class App {
        static final String READ = "app.read";
        static final String WRITE = "app.write";
        static final String DELETE = "app.delete";

        static final String[] ALL = {
                READ,
                WRITE,
                DELETE
        };
    }

    static class Workspace {
        static final String READ = "workspace.read";
        static final String WRITE = "workspace.write";
        static final String DELETE = "workspace.delete";

        static final String[] ALL;

        static {
            ALL = new String[3 + App.ALL.length];
            ALL[0] = READ;
            ALL[1] = WRITE;
            ALL[2] = DELETE;
            System.arraycopy(App.ALL, 0, ALL, 3, App.ALL.length);

        }
    }

    static {
        assertUniquenessAndCorrectness(App.ALL);
        assertUniquenessAndCorrectness(Workspace.ALL);
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
}
