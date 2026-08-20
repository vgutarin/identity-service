package vg.identity.frontend.vaadin.service;

import com.vaadin.flow.server.VaadinServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * Programmatically authenticates a user inside the current Vaadin request without going through the
 * username/password login form.
 * <p>
 * The authentication is stored both in the {@link SecurityContextHolder} (for the current request thread) and
 * in the HTTP session, so a subsequent full-page navigation is served as an authenticated request. Because we
 * bypass the {@code AuthenticationManager}, we also publish the {@code AuthenticationSuccessEvent} ourselves so
 * that success listeners fire exactly as they would for a form login.
 * <p>
 * The HTTP session id is rotated on login to defend against session fixation. Spring Security's form login
 * does this automatically via its {@code SessionManagementFilter}; this programmatic path bypasses that
 * filter, so the rotation is performed explicitly here.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class VaadinAuthenticationService {

    private final AuthenticationEventPublisher authenticationEventPublisher;

    /**
     * Authenticates the given principal for the current session.
     *
     * @return {@code true} when the security context was persisted to the HTTP session, {@code false} when no
     * Vaadin servlet request is bound to the current thread (in which case the login cannot survive the next
     * request).
     */
    public boolean authenticate(UserDetails principal) {
        return authenticate(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities())
        );
    }

    public boolean authenticate(Authentication authentication) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        var request = VaadinServletRequest.getCurrent();
        if (request == null) {
            log.warn("Cannot persist authentication: no Vaadin servlet request bound to the current thread");
            return false;
        }

        var httpRequest = request.getHttpServletRequest();
        // Ensure a session exists, then rotate its id before storing the authenticated context, so a
        // pre-login (possibly attacker-known) session id cannot outlive the login (session-fixation defense).
        httpRequest.getSession(true);
        httpRequest.changeSessionId();
        httpRequest.getSession()
                .setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        authenticationEventPublisher.publishAuthenticationSuccess(authentication);
        return true;
    }
}
