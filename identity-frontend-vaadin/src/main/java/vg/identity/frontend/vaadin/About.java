package vg.identity.frontend.vaadin;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import jakarta.annotation.security.PermitAll;
import vg.identity.frontend.vaadin.service.LocalizationService;

@PageTitle("About")
@Route(value = "about", layout = MainView.class)
@RouteAlias(value = "", layout = MainView.class)

@PermitAll
public class About extends VerticalLayout {
    public About(LocalizationService localization) {
        add(
            new Span(localization.i18n("about.description"))
        );
    }
}
