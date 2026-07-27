package vg.identity.security;

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
import vg.identity.service.IdentityApiKeyService;
import vg.unique.id.model.UniqueId;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private IdentityApiKeyService apiKeyService;
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
    }
}
