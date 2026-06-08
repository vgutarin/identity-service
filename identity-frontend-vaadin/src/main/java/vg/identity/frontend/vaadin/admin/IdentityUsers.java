package vg.identity.frontend.vaadin.admin;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import vg.identity.frontend.vaadin.MainView;
import vg.identity.frontend.vaadin.Role;
import vg.identity.frontend.vaadin.service.LocalizationService;

@PageTitle("Users")
@Route(value = "admin/users", layout = MainView.class)
@RolesAllowed(Role.IDENTITY_ADMIN)
public class IdentityUsers extends VerticalLayout {
    public IdentityUsers(LocalizationService localization) {
        add(
                new Span(localization.i18n("TODO identity users"))
        );
    }
}
