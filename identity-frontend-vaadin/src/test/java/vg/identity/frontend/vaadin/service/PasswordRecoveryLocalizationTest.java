package vg.identity.frontend.vaadin.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards SC-006 / FR-012 for the password-recovery flow: every new user-facing message key must exist in
 * both the default (uk-UA) bundle and the base bundle, and a locale with no bundle must fall back to the base
 * bundle rather than exposing a raw key.
 */
class PasswordRecoveryLocalizationTest {

    private static final List<String> RECOVERY_KEYS = List.of(
            "password.recovery.title",
            "password.recovery.prompt",
            "password.recovery.email.label",
            "password.recovery.submit",
            "password.recovery.confirmation",
            "password.reset.title",
            "password.reset.prompt",
            "password.reset.submit",
            "password.reset.success",
            "password.reset.link.missing",
            "password.reset.link.invalidOrExpired",
            "password.reset.requestNew",
            "login.forgotPassword.link"
    );

    @Test
    void everyRecoveryKeyIsPresentInBaseAndUkrainianBundles() {
        var base = load("messages.properties");
        var ukrainian = load("messages_uk_UA.properties");

        assertThat(RECOVERY_KEYS).allSatisfy(key -> {
            assertThat(base.getProperty(key))
                    .as("base bundle must define %s", key)
                    .isNotBlank();
            assertThat(ukrainian.getProperty(key))
                    .as("uk-UA bundle must define %s", key)
                    .isNotBlank();
        });
    }

    @Test
    void missingLocaleFallsBackToBaseBundleAndNeverExposesRawKey() {
        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // Deterministic fallback: a locale with no bundle resolves against the base bundle, not the host locale.
        messageSource.setFallbackToSystemLocale(false);

        var base = load("messages.properties");
        RECOVERY_KEYS.forEach(key -> {
            var frenchValue = messageSource.getMessage(key, null, java.util.Locale.FRENCH);
            assertThat(frenchValue)
                    .as("missing-locale lookup of %s must fall back to the base value, not the raw key", key)
                    .isEqualTo(base.getProperty(key))
                    .isNotEqualTo(key);
        });
    }

    private static Properties load(String resource) {
        var properties = new Properties();
        try (Reader reader = new InputStreamReader(
                new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load " + resource, e);
        }
        return properties;
    }
}
