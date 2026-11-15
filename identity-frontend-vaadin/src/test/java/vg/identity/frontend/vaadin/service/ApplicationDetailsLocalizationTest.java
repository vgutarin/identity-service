package vg.identity.frontend.vaadin.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;
import vg.identity.model.IdentityMessageFailureCode;
import vg.identity.model.IdentityMessageStatus;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationDetailsLocalizationTest {
    @Test
    void translations_whenDetailsKeysAreUsed_existInBothBundlesAndHaveFallbacks() throws Exception {
        var base = load("messages.properties");
        var ukrainian = load("messages_uk_UA.properties");
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        source.setFallbackToSystemLocale(false);

        var keys = base.stringPropertyNames().stream()
                .filter(key -> key.startsWith("application.details.") || key.equals("shell.welcome")).toList();
        var required = List.of("title", "page", "back", "workspaces", "members", "messages", "refresh", "previous",
                "next", "id", "uri", "created", "updated", "recipient", "member.name", "member.first", "member.last",
                "message.id", "message.status", "message.failure", "members.empty", "messages.empty", "unavailable",
                "noAccess", "loadError", "unknown", "failure.none", "send.action", "send.text", "send.cancel",
                "send.submit", "send.validation", "send.accepted", "send.denied", "send.unavailable", "send.error");
        assertThat(required).allSatisfy(key -> assertThat(keys).contains("application.details." + key));
        assertThat(keys).contains("shell.welcome");
        assertThat(keys).allSatisfy(key -> {
            assertThat(base.getProperty(key)).isNotBlank();
            assertThat(ukrainian.getProperty(key)).as(key).isNotBlank();
            assertThat(source.getMessage(key, null, Locale.FRENCH)).isEqualTo(base.getProperty(key));
        });
        for (var status : IdentityMessageStatus.values()) {
            assertThat(keys).contains("application.details.status." + status.name());
        }
        for (var failure : IdentityMessageFailureCode.values()) {
            assertThat(keys).contains("application.details.failure." + failure.name());
        }
    }

    @Test
    void i18n_whenArgumentsAreSupplied_formatsNumbersAndSubstitutesValues() {
        var source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding(StandardCharsets.UTF_8.name());
        var service = new LocalizationService(source);

        assertThat(service.i18n("application.details.page", 1, 2, 51))
                .isEqualTo("Сторінка 1 з 2 · Повідомлень: 51");
        assertThat(service.i18n("shell.welcome", "Alice")).isEqualTo("Вітаємо, Alice");
    }

    private Properties load(String name) throws Exception {
        var properties = new Properties();
        try (var reader = new InputStreamReader(new ClassPathResource(name).getInputStream(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
