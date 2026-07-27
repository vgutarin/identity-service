package vg.identity.frontend.vaadin.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import vg.identity.frontend.vaadin.service.LocalizationService;

/**
 * Shared helpers for the recurring dialog scaffolding used across the admin views.
 */
public final class Dialogs {

    /**
     * Default maximum width, in pixels, for a form dialog on wide viewports.
     */
    private static final int DEFAULT_FORM_WIDTH_PX = 640;

    private Dialogs() {
    }

    /**
     * Creates a draggable dialog with the given title, capped at {@link #DEFAULT_FORM_WIDTH_PX}.
     */
    public static Dialog form(String title) {
        return form(title, DEFAULT_FORM_WIDTH_PX);
    }

    /**
     * Creates a draggable dialog with the given title. The width caps at {@code maxWidthPx} on wide
     * viewports but shrinks to 92% of the viewport on narrow (mobile) screens, so the dialog never
     * overflows the screen.
     */
    public static Dialog form(String title, int maxWidthPx) {
        var dialog = new Dialog();
        dialog.setHeaderTitle(title);
        dialog.setDraggable(true);
        dialog.setWidth("min(%dpx, 92vw)".formatted(maxWidthPx));
        return dialog;
    }

    /**
     * Builds a single-column {@link FormLayout} for the given fields. A single column keeps the
     * form readable on both desktop and mobile.
     */
    public static FormLayout singleColumnForm(Component... fields) {
        var form = new FormLayout(fields);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        return form;
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
        confirmAction(localization, headerKey, textKey, "Delete", onConfirm);
    }

    /**
     * Opens a confirmation dialog for an irreversible action using the shared error styling.
     * Header, text, and action label are given as i18n keys.
     */
    public static void confirmAction(
            LocalizationService localization,
            String headerKey,
            String textKey,
            String confirmTextKey,
            Runnable onConfirm
    ) {
        var dialog = new ConfirmDialog();
        dialog.setHeader(localization.i18n(headerKey));
        dialog.setText(localization.i18n(textKey));
        dialog.setCancelable(true);
        dialog.setCancelText(localization.i18n("Cancel"));
        dialog.setConfirmText(localization.i18n(confirmTextKey));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> onConfirm.run());
        dialog.open();
    }
}
