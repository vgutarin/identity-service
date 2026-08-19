package vg.identity.rest.server.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import vg.identity.model.IdentityApiKeyPrincipal;
import vg.identity.rest.server.audit.ApiAuthenticationAuditor;
import vg.identity.service.IdentityApiKeyService;
import vg.unique.id.model.UniqueId;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private IdentityApiKeyService apiKeyService;
    @Mock
    private ApiAuthenticationAuditor auditor;
    @Mock
    private FilterChain filterChain;
    @InjectMocks
    private ApiKeyAuthenticationFilter filter;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_whenHeaderIsMissing_returnsUnauthorizedWithoutCallingChain() throws Exception {
        var response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest(), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
        verify(auditor).failure(eq("missing_header"), any(), any(), any(), eq(null));
    }

    @Test
    void doFilterInternal_whenKeyIsInvalid_auditsFailureWithKeyIdNotRawValue() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/applications/me");
        request.addHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER, "the-id.the-secret");
        var response = new MockHttpServletResponse();
        when(apiKeyService.authenticate("the-id.the-secret")).thenReturn(Optional.empty());
        when(apiKeyService.extractKeyId("the-id.the-secret")).thenReturn(Optional.of("the-id"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
        // only the key id is forwarded for auditing, never the raw key/secret
        verify(auditor).failure(eq("invalid_key"), eq("GET"), eq("/api/v1/applications/me"), any(), eq("the-id"));
    }

    @Test
    void doFilterInternal_whenKeyIsValid_exposesPrincipalOnlyForCurrentRequest() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(ApiKeyAuthenticationFilter.API_KEY_HEADER, "key-value");
        var response = new MockHttpServletResponse();
        var observedPrincipal = new AtomicReference<Object>();
        when(apiKeyService.authenticate("key-value")).thenReturn(Optional.of(new IdentityApiKeyPrincipal(
                new UniqueId(42L),
                "https://example.test/application"
        )));
        doAnswer(invocation -> {
            observedPrincipal.set(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
            return null;
        }).when(filterChain).doFilter(any(), any());

        filter.doFilter(request, response, filterChain);

        assertThat(observedPrincipal.get()).isInstanceOf(IdentityApiKeyPrincipal.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(auditor).success(any(), any(), any());
    }
}
