package vg.identity.frontend.vaadin;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import vg.identity.service.IdentityUserChannelVerificationService;

import java.util.UUID;

@Route("channel/verify")
@PageTitle("Channel verification")
@AnonymousAllowed
public class IdentityUserChannelVerificationView extends VerticalLayout implements BeforeEnterObserver {
    private final transient IdentityUserChannelVerificationService verificationService;
    private final Span result = new Span();

    public IdentityUserChannelVerificationView(IdentityUserChannelVerificationService verificationService) {
        this.verificationService = verificationService;

        setSizeFull();
        setJustifyContentMode(JustifyContentMode.CENTER);
        setAlignItems(Alignment.CENTER);

        add(new H1("Channel verification"), result);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        var id = event.getLocation()
                .getQueryParameters()
                .getParameters()
                .getOrDefault("id", java.util.List.of())
                .stream()
                .findFirst()
                .orElse(null);

        if (id == null || id.isBlank()) {
            result.setText("Verification link is missing.");
            return;
        }

        try {
            var verified = verificationService.verify(UUID.fromString(id));
            result.setText(verified
                    ? "Channel verified."
                    : "Verification link is invalid or expired."
            );
        } catch (IllegalArgumentException e) {
            result.setText("Verification link is invalid or expired.");
        }
    }
}
