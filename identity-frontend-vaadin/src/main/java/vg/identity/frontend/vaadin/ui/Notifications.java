package vg.identity.frontend.vaadin.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Shared helpers for showing toast notifications with consistent duration and position.
 */
public final class Notifications {

    private static final int DURATION_MS = 5000;
    private static final Notification.Position POSITION = Notification.Position.TOP_END;

    private Notifications() {
    }

    public static void success(String message) {
        show(message, NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        show(message, NotificationVariant.LUMO_ERROR);
    }

    public static void show(String message, NotificationVariant variant) {
        var notification = Notification.show(message, DURATION_MS, POSITION);
        notification.addThemeVariants(variant);
    }
}
