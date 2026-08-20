package vg.identity.frontend.vaadin.auth;

import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.RouterLink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vg.identity.frontend.vaadin.service.LocalizationService;
import vg.identity.model.IdentityAction;
import vg.identity.service.IdentityActionTokenService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PasswordResetViewTest extends SpringBrowserlessTest {

    @MockitoBean
    private IdentityActionTokenService actionTokenService;
    @Autowired
    private LocalizationService localization;

    @Test
    void missingKey_showsLinkMissingAndOffersRequestNewLink() {
        navigate("reset/password", PasswordResetView.class);

        assertMessageShown("password.reset.link.missing");
        assertRequestNewLinkPresent();
    }

    @Test
    void invalidOrExpiredKey_showsInvalidMessageAndOffersRequestNewLink() {
        when(actionTokenService.findResetPasswordActionInfo(anyString())).thenReturn(null);

        navigate(PasswordResetView.class, Map.of(PasswordResetView.ID_PARAM, "3_badkey"));

        assertMessageShown("password.reset.link.invalidOrExpired");
        assertRequestNewLinkPresent();
    }

    @Test
    void validKey_rendersSetPasswordForm() {
        when(actionTokenService.findResetPasswordActionInfo(anyString()))
                .thenReturn(new IdentityAction.ResetPasswordInfo("3_goodkey"));

        navigate(PasswordResetView.class, Map.of(PasswordResetView.ID_PARAM, "3_goodkey"));

        // SetPasswordForm renders two PasswordFields (password + confirm); no dead-end link is shown.
        assertThat(find(PasswordField.class).all()).hasSize(2);
        assertMessageShown("password.reset.prompt");
        assertThat(requestNewLinkExists()).isFalse();
    }

    private void assertMessageShown(String key) {
        assertThat(find(Span.class).all())
                .anyMatch(span -> localization.i18n(key).equals(span.getText()));
    }

    private void assertRequestNewLinkPresent() {
        assertThat(requestNewLinkExists())
                .as("a 'request a new link' RouterLink should be offered on a dead-end reset link")
                .isTrue();
    }

    private boolean requestNewLinkExists() {
        var label = localization.i18n("password.reset.requestNew");
        return find(RouterLink.class).all().stream().anyMatch(link -> label.equals(link.getText()));
    }
}
