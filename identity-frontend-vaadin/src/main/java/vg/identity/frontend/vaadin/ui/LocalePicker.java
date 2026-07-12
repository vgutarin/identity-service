package vg.identity.frontend.vaadin.ui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.select.Select;
import vg.identity.frontend.vaadin.service.LocalizationService;

import java.util.Locale;

/**
 * Language selector bound to the {@link LocalizationService}. Switching the value updates the
 * current locale and reloads the page so all translations are re-rendered.
 */
public class LocalePicker extends Select<Locale> {

    private final transient LocalizationService localization;

    public LocalePicker(LocalizationService localization) {
        this.localization = localization;

        addClassName("locale-picker");
        setLabel(localization.i18n("Language"));
        setItems(localization.getProvidedLocales());
        setItemLabelGenerator(this::localeName);
        setValue(localization.getCurrentLocale());
        addValueChangeListener(event -> {
            if (event.isFromClient() && null != event.getValue()) {
                localization.setCurrentLocale(event.getValue());
                UI.getCurrent().getPage().reload();
            }
        });
    }

    private String localeName(Locale locale) {
        return localization.i18n("locale." + locale.toLanguageTag());
    }
}
