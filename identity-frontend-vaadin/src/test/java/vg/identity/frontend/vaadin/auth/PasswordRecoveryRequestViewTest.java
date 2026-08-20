package vg.identity.frontend.vaadin.auth;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.browserless.SpringBrowserlessTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.service.IdentityActionTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class PasswordRecoveryRequestViewTest extends SpringBrowserlessTest {

    @MockitoBean
    private IdentityActionTokenService actionTokenService;
    @Autowired
    private LocalizationService localization;

    @Test
    void submit_withEmail_showsNeutralConfirmationAndRequestsReset() {
        navigate(PasswordRecoveryRequestView.class);

        test(find(EmailField.class).single()).setValue("john@example.com");
        test(find(Button.class).single()).click();

        // The neutral confirmation replaces the form; the service was asked to process the request.
        verify(actionTokenService).requestPasswordReset(eq("john@example.com"), any());
        assertConfirmationShown();
        assertThat(find(EmailField.class).exists()).isFalse();
    }

    @Test
    void submit_withBlankEmail_showsSameConfirmationWithoutCallingService() {
        navigate(PasswordRecoveryRequestView.class);

        // Submit with no input: the same neutral confirmation appears and nothing is leaked or thrown.
        test(find(Button.class).single()).click();

        verify(actionTokenService, never()).requestPasswordReset(any(), any());
        assertConfirmationShown();
    }

    private void assertConfirmationShown() {
        assertThat(find(Span.class).all())
                .anyMatch(span -> localization.i18n("password.recovery.confirmation").equals(span.getText()));
    }
}
