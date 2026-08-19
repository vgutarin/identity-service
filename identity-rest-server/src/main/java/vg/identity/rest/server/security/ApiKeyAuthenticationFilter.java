package vg.identity.rest.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vg.identity.rest.server.audit.ApiAuthenticationAuditor;
import vg.identity.service.IdentityApiKeyService;

import java.io.IOException;
import java.util.Collections;

/**
 * Authenticates the single {@value API_KEY_HEADER} request header for the REST API.
 *
 * <p>Registered only when {@code identity.rest.api.enabled=true} (see
 * {@link vg.identity.rest.server.controller.IdentityApplicationController} for why the condition is
 * on the class).</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("identity.rest.api.enabled")
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    public static final String API_KEY_HEADER = "X-VG-Identity-API-Key";

    private final IdentityApiKeyService apiKeyService;
    private final ApiAuthenticationAuditor auditor;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var values = Collections.list(request.getHeaders(API_KEY_HEADER));
        if (values.size() != 1) {
            var reason = values.isEmpty() ? "missing_header" : "multiple_headers";
            auditor.failure(reason, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), null);
            unauthorized(response);
            return;
        }

        var rawKey = values.getFirst();
        var principal = apiKeyService.authenticate(rawKey).orElse(null);
        if (principal == null) {
            auditor.failure(
                    "invalid_key",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr(),
                    apiKeyService.extractKeyId(rawKey).orElse(null)
            );
            unauthorized(response);
            return;
        }

        auditor.success(principal.getUniqueId().toString(), request.getMethod(), request.getRequestURI());

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new PreAuthenticatedAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        ));
        SecurityContextHolder.setContext(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void unauthorized(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
