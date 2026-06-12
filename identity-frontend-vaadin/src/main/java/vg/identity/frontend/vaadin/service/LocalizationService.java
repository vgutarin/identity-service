package vg.identity.frontend.vaadin.service;

import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/**
 * TODO implement real logic
 * Expectation are
 *  1. Correct handling user timezone
 *  2. Correct date time formatting (??? based on explicit user choice)
 */
@Service
@Slf4j
public class LocalizationService implements I18NProvider {

    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("uk-UA");
    private static final List<Locale> PROVIDED_LOCALES = List.of(
            DEFAULT_LOCALE,
            Locale.ENGLISH
    );

    private final MessageSource messageSource;

    public LocalizationService(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public DateTimePicker newDateTimePicker(String label) {
        var dateTimePicker = new DateTimePicker(i18n(label));
        dateTimePicker.setLocale(currentLocale());
        return dateTimePicker;
    }

    public void setValue(DateTimePicker dateTimePicker, Instant value) {
        dateTimePicker.setValue(toLocalDateTime(value));
    }

    public Instant getInstant(DateTimePicker dateTimePicker) {
        return dateTimePicker
                .getOptionalValue()
                .map(v -> v.toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        //TODO implement real logic
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public String i18n(Exception e) {
        var simpleName = "exception." + e.getClass().getSimpleName();
        var result = i18n(simpleName);
        if (!simpleName.equals(result)) {
            return result;
        }

        log.warn("Cannot find i18n", e);
        return i18n("exception.unknown");
    }

    public String i18n(String key) {
        return getTranslation(key, currentLocale());
    }

    public Locale getCurrentLocale() {
        return currentLocale();
    }

    public void setCurrentLocale(Locale locale) {
        var normalizedLocale = normalizeLocale(locale);
        var session = VaadinSession.getCurrent();
        if (null != session) {
            session.setAttribute(Locale.class, normalizedLocale);
        }

        var ui = UI.getCurrent();
        if (null != ui) {
            ui.setLocale(normalizedLocale);
        }
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return PROVIDED_LOCALES;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        if (null == key) {
            return null;
        }
        return messageSource.getMessage(key, params, key, normalizeLocale(locale));
    }

    private Locale currentLocale() {
        var session = VaadinSession.getCurrent();
        if (null != session) {
            var sessionLocale = session.getAttribute(Locale.class);
            if (null != sessionLocale) {
                return sessionLocale;
            }
        }

        var ui = UI.getCurrent();
        if (null != ui && null != ui.getLocale()) {
            return ui.getLocale();
        }
        return normalizeLocale(LocaleContextHolder.getLocale());
    }

    private Locale normalizeLocale(Locale locale) {
        if (null == locale) {
            return DEFAULT_LOCALE;
        }
        return locale;
    }


}
