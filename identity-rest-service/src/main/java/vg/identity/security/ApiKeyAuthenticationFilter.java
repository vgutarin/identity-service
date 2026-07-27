package vg.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vg.identity.service.IdentityApiKeyService;

import java.io.IOException;
import java.util.Collections;

/**
 * Authenticates the single {@value API_KEY_HEADER} request header for the REST API.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    public static final String API_KEY_HEADER = "X-VG-Identity-API-Key";

    private final IdentityApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var values = Collections.list(request.getHeaders(API_KEY_HEADER));
        if (values.size() != 1) {
            unauthorized(response);
            return;
        }

        var principal = apiKeyService.authenticate(values.getFirst()).orElse(null);
        if (principal == null) {
            unauthorized(response);
            return;
        }

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
