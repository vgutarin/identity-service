package vg.identity.frontend.vaadin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.userdetails.UserDetails;
import vg.identity.frontend.vaadin.admin.IdentityUsers;
import vg.identity.frontend.vaadin.admin.IdentityUsersChannels;
import vg.identity.frontend.vaadin.service.LocalizationService;

import java.util.Locale;

//@CssImport("./styles/views/main/main-view.css")
//@JsModule("./styles/shared-styles.js")
@PermitAll
public class MainView extends AppLayout implements AfterNavigationObserver {

    private final transient AuthenticationContext authContext;
    private final LocalizationService localization;

    private H3 viewTitle;

    public MainView(LocalizationService localizationService, AuthenticationContext authContext) {
        this.authContext = authContext;
        this.localization = localizationService;
        // Use the drawer for the menu
        setPrimarySection(Section.DRAWER);

        // Make the nav bar a header
        addToNavbar(true, createHeaderContent());

        // Put the menu in the drawer
        var menu = createMenu();
        addToDrawer(createDrawerContent(menu));
    }

    private Component createHeaderContent() {
        var layout = new HorizontalLayout();

        // Configure styling for the header
        layout.addClassName("main-header");
        layout.setWidthFull();
        layout.setSpacing(false);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        // Have the drawer toggle button on the left
        layout.add(new DrawerToggle());

        // Placeholder for the title of the current view.
        // The title will be set after navigation.
        viewTitle = new H3();
        viewTitle.addClassName("view-title");
        layout.add(viewTitle);
        layout.expand(viewTitle);

        layout.add(createLocalePicker());

        layout.add(
                authContext.getAuthenticatedUser(UserDetails.class)
                        .map(user -> {
                            var logout = new Button(localization.i18n("Logout"), click -> this.authContext.logout());
                            var loggedUser = new Span("Welcome " + user.getUsername());
                            loggedUser.addClassName("logged-user");
                            var userActions = new HorizontalLayout(loggedUser, logout);
                            userActions.addClassName("user-actions");
                            userActions.setAlignItems(FlexComponent.Alignment.CENTER);
                            return userActions;
                        }).orElseGet(() ->
                                {
                                    var userActions = new HorizontalLayout(
                                            new Button(localization.i18n("Login"), click -> {/*TODO*/})
                                    );
                                    userActions.addClassName("user-actions");
                                    userActions.setAlignItems(FlexComponent.Alignment.CENTER);
                                    return userActions;
                                }
                        )
        );

        // A user icon
        //layout.add(new Image("images/user.svg", "Avatar"));

        return layout;
    }

    private Select<Locale> createLocalePicker() {
        var localePicker = new Select<Locale>();
        localePicker.addClassName("locale-picker");
        localePicker.setLabel(localization.i18n("Language"));
        localePicker.setItems(localization.getProvidedLocales());
        localePicker.setItemLabelGenerator(this::localeName);
        localePicker.setValue(localization.getCurrentLocale());
        localePicker.addValueChangeListener(event -> {
            if (event.isFromClient() && null != event.getValue()) {
                localization.setCurrentLocale(event.getValue());
                UI.getCurrent().getPage().reload();
            }
        });
        return localePicker;
    }

    private String localeName(Locale locale) {
        return localization.i18n("locale." + locale.toLanguageTag());
    }

    private Component createDrawerContent(SideNav menu) {
        var layout = new VerticalLayout();

        // Configure styling for the drawer
        layout.addClassName("drawer-content");
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.setAlignItems(FlexComponent.Alignment.STRETCH);

        // Have a drawer header with an application logo
        var logoLayout = new HorizontalLayout();
        logoLayout.addClassName("drawer-logo");
        logoLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        //logoLayout.add(new Image("images/logo.jpeg", "My Project logo"));
        logoLayout.add(new H1(localization.i18n("project.name")));

        // Display the logo and the menu in the drawer
        layout.add(logoLayout, menu);
        return layout;
    }

    private SideNav createMenu() {
        var navMenu = new SideNav();

        if (authContext.hasRole(Role.IDENTITY_ADMIN)) {
            var adminGroup = new SideNavItem(localization.i18n("Admin"));
            adminGroup.setPrefixComponent(VaadinIcon.COG.create());
            adminGroup.addItem(
                    sideNavItem(IdentityUsers.class),
                    sideNavItem(IdentityUsersChannels.class)
            );
            navMenu.addItem(adminGroup);
        }

        navMenu.addItem(
                sideNavItem(About.class)
        );
        return navMenu;
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {

        // Set the view title in the header
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        return getPageTitle(getContent().getClass());
    }

    private SideNavItem sideNavItem(Class<? extends Component> navigationTarget, Component prefix) {
        return new SideNavItem(getPageTitle(navigationTarget), navigationTarget, prefix);
    }

    private SideNavItem sideNavItem(Class<? extends Component> navigationTarget) {
        return sideNavItem(navigationTarget, null);
    }

    private String getPageTitle(Class<?> c) {
        return localization.i18n(c.getAnnotation(PageTitle.class).value());
    }
}
