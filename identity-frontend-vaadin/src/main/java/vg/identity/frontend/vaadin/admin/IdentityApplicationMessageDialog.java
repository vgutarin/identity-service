package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import org.springframework.security.access.AccessDeniedException;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.frontend.vaadin.ui.Dialogs;
import vg.identity.frontend.vaadin.ui.Notifications;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityApplicationUser;
import vg.identity.model.IdentityMessageDeliveryUnavailableException;
import vg.identity.model.MessageContent;
import vg.identity.service.IdentityApplicationMessageService;
import vg.unique.id.model.UniqueId;

import java.util.UUID;

final class IdentityApplicationMessageDialog {
    private final UniqueId applicationUniqueId;
    private final UniqueId userUniqueId;
    private final IdentityApplicationMessageService messages;
    private final LocalizationService localization;
    private final Dialog dialog;
    private final TextArea text;
    private final Span error = new Span();
    private final Button send;
    private IdentityApplicationMessageRequest request;

    IdentityApplicationMessageDialog(UniqueId applicationUniqueId, IdentityApplicationUser recipient,
                                     IdentityApplicationMessageService messages, LocalizationService localization) {
        this.applicationUniqueId = applicationUniqueId;
        this.userUniqueId = recipient.uniqueId();
        this.messages = messages;
        this.localization = localization;
        dialog = Dialogs.form(label("action"));
        dialog.setCloseOnOutsideClick(false);
        text = new TextArea(label("text"));
        text.setWidthFull();
        text.setMinHeight("10em");
        text.setRequiredIndicatorVisible(true);
        text.setManualValidation(true);
        error.getElement().setAttribute("role", "alert");
        error.setVisible(false);
        var recipientLabel = recipient.displayName() == null || recipient.displayName().isBlank()
                ? userUniqueId.toString() : recipient.displayName() + " (" + userUniqueId + ")";
        dialog.add(new Span(recipientLabel), text, error);
        var cancel = new Button(label("cancel"), event -> dialog.close());
        send = new Button(label("submit"), event -> submit());
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        send.setDisableOnClick(true);
        dialog.getFooter().add(Dialogs.footer(cancel, send));
    }

    void open() {
        dialog.open();
        text.focus();
    }

    private void submit() {
        if (!dialog.isOpened()) {
            return;
        }
        error.setVisible(false);
        text.setInvalid(false);
        MessageContent content;
        try {
            content = MessageContent.builder().text(text.getValue()).build();
        } catch (IllegalArgumentException ignored) {
            text.setErrorMessage(label("validation"));
            text.setInvalid(true);
            send.setEnabled(true);
            return;
        }
        // Preserve the key after an uncertain submission result; edited content is a new submission.
        if (request == null || !request.content().equals(content)) {
            request = IdentityApplicationMessageRequest.builder().content(content).idempotencyKey(UUID.randomUUID()).build();
        }
        try {
            messages.submitMessage(applicationUniqueId, userUniqueId, request);
            dialog.close();
            Notifications.success(label("accepted"));
        } catch (AccessDeniedException ignored) {
            showError("denied");
        } catch (IdentityMessageDeliveryUnavailableException ignored) {
            showError("unavailable");
        } catch (Exception ignored) {
            showError("error");
        } finally {
            if (dialog.isOpened()) {
                send.setEnabled(true);
            }
        }
    }

    private void showError(String key) {
        error.setText(label(key));
        error.setVisible(true);
    }

    private String label(String key) {
        return localization.i18n("application.details.send." + key);
    }
}
