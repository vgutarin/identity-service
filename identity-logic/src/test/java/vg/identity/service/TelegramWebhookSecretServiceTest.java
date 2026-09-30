package vg.identity.service;

import org.junit.jupiter.api.Test;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class TelegramWebhookSecretServiceTest {

    @Test
    void matches_acceptsOnlyTheDerivedSecretForThatApplication() {
        var service = new TelegramWebhookSecretService("test-webhook-secret-with-at-least-thirty-two-characters", "", true);
        var application = new UniqueId(42L);

        var secret = service.secretFor(application, 100L);

        assertThat(secret).matches("[A-Za-z0-9_-]{32,256}");
        assertThat(service.matches(application, 100L, secret)).isTrue();
        assertThat(service.matches(new UniqueId(43L), 100L, secret)).isFalse();
        assertThat(service.matches(application, 101L, secret)).isFalse();
        assertThat(service.matches(application, 100L, "different-secret")).isFalse();
        assertThat(service.matches(application, 100L, null)).isFalse();
    }

    @Test
    void matches_duringRotationAcceptsSecretsDerivedFromEitherMasterSecret() {
        var previousService = new TelegramWebhookSecretService("previous-webhook-secret-with-at-least-thirty-two-characters", "", true);
        var service = new TelegramWebhookSecretService(
                "current-webhook-secret-with-at-least-thirty-two-characters",
                "previous-webhook-secret-with-at-least-thirty-two-characters",
                true
        );
        var application = new UniqueId(42L);

        assertThat(service.matches(application, 100L, service.secretFor(application, 100L))).isTrue();
        assertThat(service.matches(application, 100L, previousService.secretFor(application, 100L))).isTrue();
        assertThat(service.matches(new UniqueId(43L), 100L, previousService.secretFor(application, 100L))).isFalse();
        assertThat(service.matches(application, 101L, previousService.secretFor(application, 100L))).isFalse();
    }

    @Test
    void constructor_whenEnabledWithoutAValidSecret_failsFast() {
        assertThatIllegalStateException().isThrownBy(() -> new TelegramWebhookSecretService("x".repeat(31), "", true))
                .withMessageContaining("identity.telegram.webhook.secret")
                .withMessageContaining("32-256 characters");
    }

    @Test
    void constructor_whenEnabledWithAnInvalidPreviousSecret_failsFast() {
        assertThatIllegalStateException().isThrownBy(() -> new TelegramWebhookSecretService(
                "current-webhook-secret-with-at-least-thirty-two-characters",
                "x".repeat(31),
                true
        ))
                .withMessageContaining("identity.telegram.webhook.previous-secret")
                .withMessageContaining("32-256 characters");
    }

    @Test
    void constructor_whenEnabledSecretHasLeadingOrTrailingWhitespace_failsFast() {
        assertThatIllegalStateException().isThrownBy(() -> new TelegramWebhookSecretService(
                " " + "x".repeat(32),
                "",
                true
        ))
                .withMessageContaining("identity.telegram.webhook.secret");
        assertThatIllegalStateException().isThrownBy(() -> new TelegramWebhookSecretService(
                "x".repeat(32),
                "x".repeat(32) + " ",
                true
        ))
                .withMessageContaining("identity.telegram.webhook.previous-secret");
    }

    @Test
    void constructor_whenEnabledSecretContainsUnsupportedCharactersOrExceedsTelegramLimit_failsFast() {
        assertThatIllegalStateException().isThrownBy(() -> new TelegramWebhookSecretService(
                "x".repeat(32) + "!",
                "",
                true
        ))
                .withMessageContaining("identity.telegram.webhook.secret");
        assertThatIllegalStateException().isThrownBy(() -> new TelegramWebhookSecretService(
                "x".repeat(32),
                "x".repeat(257),
                true
        ))
                .withMessageContaining("identity.telegram.webhook.previous-secret");
    }

    @Test
    void disabledWebhookNeverAuthenticatesOrDerivesAWebhookSecret() {
        var service = new TelegramWebhookSecretService("too-short", "also-too-short", false);

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.matches(new UniqueId(42L), 100L, "anything")).isFalse();
        assertThatIllegalStateException().isThrownBy(() -> service.secretFor(new UniqueId(42L), 100L))
                .withMessageContaining("identity.telegram.webhook.enabled");
    }
}
