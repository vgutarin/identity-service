package vg.identity.frontend.vaadin.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import vg.identity.frontend.vaadin.service.LocalizationService;

/**
 * Shared helpers for the recurring dialog scaffolding used across the admin views.
 */
public final class Dialogs {

    private Dialogs() {
    }

    /**
     * Builds a right-aligned footer row for the given buttons (typically cancel + save).
     */
    public static HorizontalLayout footer(Button... buttons) {
        var footer = new HorizontalLayout(buttons);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        return footer;
    }

    /**
     * Opens a delete confirmation dialog with the shared "error primary" styling. Header and text
     * are given as i18n keys and localized here; the confirm action runs {@code onConfirm}.
     */
    public static void confirmDelete(
            LocalizationService localization,
            String headerKey,
            String textKey,
            Runnable onConfirm
    ) {
        var dialog = new ConfirmDialog();
        dialog.setHeader(localization.i18n(headerKey));
        dialog.setText(localization.i18n(textKey));
        dialog.setCancelable(true);
        dialog.setCancelText(localization.i18n("Cancel"));
        dialog.setConfirmText(localization.i18n("Delete"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> onConfirm.run());
        dialog.open();
    }
}
