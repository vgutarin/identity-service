package vg.identity.frontend.vaadin.service;

import com.vaadin.flow.server.VaadinServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VaadinAuthenticationServiceTest {

    @Mock
    private AuthenticationEventPublisher authenticationEventPublisher;

    @InjectMocks
    private VaadinAuthenticationService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticate_rotatesSessionIdBeforeStoringContextAndPublishesSuccess() {
        var httpRequest = mock(HttpServletRequest.class);
        var session = mock(HttpSession.class);
        when(httpRequest.getSession(true)).thenReturn(session);
        when(httpRequest.getSession()).thenReturn(session);
        var vaadinRequest = mock(VaadinServletRequest.class);
        when(vaadinRequest.getHttpServletRequest()).thenReturn(httpRequest);

        Authentication auth = UsernamePasswordAuthenticationToken.authenticated("john", null, List.of());

        boolean result;
        try (var mocked = mockStatic(VaadinServletRequest.class)) {
            mocked.when(VaadinServletRequest::getCurrent).thenReturn(vaadinRequest);
            result = service.authenticate(auth);
        }

        assertThat(result).isTrue();
        // Session id is rotated before the authenticated context is stored (session-fixation defense).
        InOrder order = inOrder(httpRequest, session, authenticationEventPublisher);
        order.verify(httpRequest).changeSessionId();
        order.verify(session).setAttribute(
                eq(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY), any());
        order.verify(authenticationEventPublisher).publishAuthenticationSuccess(auth);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(auth);
    }

    @Test
    void authenticate_whenNoVaadinRequest_returnsFalseAndDoesNotPublish() {
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated("john", null, List.of());

        boolean result;
        try (var mocked = mockStatic(VaadinServletRequest.class)) {
            mocked.when(VaadinServletRequest::getCurrent).thenReturn(null);
            result = service.authenticate(auth);
        }

        assertThat(result).isFalse();
        verifyNoInteractions(authenticationEventPublisher);
    }
}
