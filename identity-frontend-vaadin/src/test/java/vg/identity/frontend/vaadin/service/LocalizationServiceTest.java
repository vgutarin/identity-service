package vg.identity.frontend.vaadin.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizationServiceTest {

    private LocalizationService service;

    @BeforeEach
    void setUp() {
        var messageSource = new StaticMessageSource();
        messageSource.addMessage("exception.telegram.botToken.required", Locale.ENGLISH, "Telegram bot token is required.");
        messageSource.addMessage("exception.optimisticLocking", Locale.ENGLISH, "Cannot save changes. Refresh the page.");
        messageSource.addMessage("unknown.error", Locale.ENGLISH, "Unknown error.");

        service = new LocalizationService(messageSource);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void i18n_whenExceptionMessageIsTranslationKey_returnsTranslation() {
        assertThat(service.i18n(new IllegalArgumentException("exception.telegram.botToken.required")))
                .isEqualTo("Telegram bot token is required.");
    }

    @Test
    void i18n_whenExceptionIsOptimisticLockingFailure_returnsOptimisticLockingTranslation() {
        assertThat(service.i18n(new OptimisticLockingFailureException("low-level optimistic locking message")))
                .isEqualTo("Cannot save changes. Refresh the page.");
    }

    @Test
    void i18n_whenExceptionMessageIsUnknown_returnsUnknownTranslation() {
        assertThat(service.i18n(new IllegalArgumentException("Unknown message")))
                .isEqualTo("Unknown error.");
    }
}
