package vg.identity.frontend.vaadin.admin;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.model.IdentityApplicationMessageRequest;
import vg.identity.model.IdentityApplicationUser;
import vg.identity.model.IdentityMessageDeliveryUnavailableException;
import vg.identity.model.IdentityMessageSubmission;
import vg.identity.service.IdentityApplicationMessageService;
import vg.unique.id.model.UniqueId;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ViewPackages(classes = IdentityApplicationDetails.class)
class IdentityApplicationMessageDialogTest extends BrowserlessTest {
    private static final UniqueId APP = new UniqueId(42L);
    private static final UniqueId USER = new UniqueId(2L);
    @Mock private IdentityApplicationMessageService messages;
    @Mock private LocalizationService localization;

    @BeforeEach
    void setUp() {
        when(localization.i18n(anyString())).thenAnswer(call -> call.getArgument(0) + " translated");
        new IdentityApplicationMessageDialog(APP, IdentityApplicationUser.builder().uniqueId(USER)
                .displayName("Alice").build(), messages, localization).open();
    }

    @Test
    void cancel_whenDraftExists_closesWithoutSubmitting() {
        field().setValue("Draft");
        var dialog = find(Dialog.class).single();

        test(button("cancel")).click();

        assertThat(dialog.isOpened()).isFalse();
        verifyNoInteractions(messages);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \n\t", "too-long"})
    void send_whenTextIsInvalid_showsValidationWithoutSubmitting(String value) {
        field().setValue(value.equals("too-long") ? "x".repeat(4097) : value);

        test(button("submit")).click();

        assertThat(field().isInvalid()).isTrue();
        assertThat(field().getErrorMessage()).isEqualTo(label("validation"));
        assertThat(find(Dialog.class).single().isOpened()).isTrue();
        assertThat(button("submit").isEnabled()).isTrue();
        verifyNoInteractions(messages);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  <b>Hello</b> {BOT_URI}\n", "unicode-limit"})
    void send_whenTextIsValid_submitsExactContentAndRequiredKeyThenCloses(String value) {
        var content = value.equals("unicode-limit") ? "😀".repeat(4096) : value;
        var requests = successfulSubmissions();
        field().setValue(content);
        var dialog = find(Dialog.class).single();
        var send = button("submit");

        test(send).click();
        send.click();

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().content().text()).isEqualTo(content);
        assertThat(requests.getFirst().idempotencyKey()).isNotNull();
        assertThat(dialog.isOpened()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"denied", "unavailable", "error"})
    void send_whenSubmissionFails_preservesDraftAndShowsOnlyLocalizedError(String failure) {
        RuntimeException exception = switch (failure) {
            case "denied" -> new AccessDeniedException("private-token-and-text");
            case "unavailable" -> new IdentityMessageDeliveryUnavailableException();
            default -> new IllegalStateException("private-token-and-text");
        };
        when(messages.submitMessage(eq(APP), eq(USER), any())).thenThrow(exception);
        field().setValue("Keep this text");

        test(button("submit")).click();

        assertThat(field().getValue()).isEqualTo("Keep this text");
        assertThat(errorText()).isEqualTo(label(failure)).doesNotContain("private-token-and-text");
        assertThat(find(Dialog.class).single().isOpened()).isTrue();
        assertThat(button("submit").isEnabled()).isTrue();
    }

    @Test
    void send_whenUnchangedTextIsRetried_reusesOriginalRequestAndKey() {
        var requests = new ArrayList<IdentityApplicationMessageRequest>();
        when(messages.submitMessage(eq(APP), eq(USER), any())).thenAnswer(call -> {
            requests.add(call.getArgument(2));
            if (requests.size() == 1) {
                throw new IllegalStateException("Uncertain acceptance");
            }
            return IdentityMessageSubmission.builder().replayed(true).build();
        });
        field().setValue("Same text");

        test(button("submit")).click();
        test(button("submit")).click();

        assertThat(requests).hasSize(2);
        assertThat(requests.get(1)).isSameAs(requests.getFirst());
    }

    @Test
    void send_whenTextIsEditedAfterFailure_usesNewKeyForChangedContent() {
        var requests = new ArrayList<IdentityApplicationMessageRequest>();
        when(messages.submitMessage(eq(APP), eq(USER), any())).thenAnswer(call -> {
            requests.add(call.getArgument(2));
            throw new IllegalStateException("Submission failed");
        });
        field().setValue("Original");
        test(button("submit")).click();
        field().setValue("Edited");

        test(button("submit")).click();

        assertThat(requests).hasSize(2);
        assertThat(requests.get(1).idempotencyKey()).isNotEqualTo(requests.getFirst().idempotencyKey());
        assertThat(requests.get(1).content().text()).isEqualTo("Edited");
    }

    private List<IdentityApplicationMessageRequest> successfulSubmissions() {
        var requests = new ArrayList<IdentityApplicationMessageRequest>();
        when(messages.submitMessage(eq(APP), eq(USER), any())).thenAnswer(call -> {
            requests.add(call.getArgument(2));
            return IdentityMessageSubmission.builder().build();
        });
        return requests;
    }

    private TextArea field() {
        return find(TextArea.class).single();
    }

    private Button button(String key) {
        return find(Button.class).all().stream().filter(button -> button.getText().equals(label(key)))
                .findFirst().orElseThrow();
    }

    private String errorText() {
        return find(Span.class).all().stream()
                .filter(span -> "alert".equals(span.getElement().getAttribute("role")))
                .map(Span::getText).findFirst().orElseThrow();
    }

    private String label(String key) {
        return "application.details.send." + key + " translated";
    }
}
