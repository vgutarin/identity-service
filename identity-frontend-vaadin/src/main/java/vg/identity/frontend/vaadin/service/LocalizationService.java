package vg.identity.frontend.vaadin.service;

import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * TODO implement real logic
 * Expectation are
 *  1. Correct handling user timezone
 *  2. Correct date time formatting (??? based on explicit user choice)
 */
@Service
@Slf4j
public class LocalizationService {

    public DateTimePicker newDateTimePicker(String label) {
        var dateTimePicker = new DateTimePicker(i18n(label));
        dateTimePicker.setLocale(Locale.forLanguageTag("uk-UA"));
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

        if (null == key) {
            return null;
        }

        return switch (key) {
            case "About" -> "Про нас";
            case "Add" -> "Додати";
            case "Apply" -> "Застосувати";
            case "Edit" -> "Редагувати";
            case "Editing" -> "Редагування";
            case "End" -> "Завершення";
            case "Cancel" -> "Відминити";
            case "Create" -> "Створити";
            case "Save" -> "Зберегти";
            case "Start" -> "Початок";
            case "Login" -> "Увійти";
            case "Logout" -> "Вийти";
            case "Groups" -> "Групи";

            case "project.name" -> "Сервіс ідентіфікаціі";

            case "about.description" -> """
                        Безпека даних це є головна мета цього сервісу.
                    """;

            case "exception.unknown" -> "Не відома помилка";
            case "exception.ObjectOptimisticLockingFailureException" -> "Не можливо зберегти зміни. Оновить сторінку.";

            case "Admin" -> "Адміністрування";
            case "Identity service" -> "Сервіс ідентифікації";
            case "Users" -> "Користувачі";
            case "Users channels" -> "Зарееєтровані користувачі";
            default -> key;
        };
    }


}
