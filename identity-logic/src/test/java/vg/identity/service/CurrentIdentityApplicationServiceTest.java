package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import vg.identity.model.IdentityApplicationPrincipal;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentIdentityApplicationServiceTest {
    @Mock
    private CurrentUserService currentUserService;

    private CurrentIdentityApplicationService service;

    @BeforeEach
    void setUp() {
        service = new CurrentIdentityApplicationService(currentUserService, "");
    }

    @Test
    void requireApplicationUniqueId_whenApiKeyApplicationIsAuthenticated_returnsItsId() {
        var expected = new UniqueId(42L);
        var principal = mock(IdentityApplicationPrincipal.class);
        when(principal.getUniqueId()).thenReturn(expected);
        when(currentUserService.findCurrentUserDetails()).thenReturn(principal);

        assertThat(service.requireApplicationUniqueId()).isEqualTo(expected);
    }

    @Test
    void requireApplicationUniqueId_whenEmbeddedApplicationIsConfiguredAndNoPrincipalExists_returnsConfiguredId() {
        var embedded = new CurrentIdentityApplicationService(currentUserService, "42");

        assertThat(embedded.requireApplicationUniqueId()).isEqualTo(new UniqueId(42L));
    }

    @Test
    void requireApplicationUniqueId_whenNonApplicationPrincipalIsAuthenticated_deniesInsteadOfUsingEmbeddedFallback() {
        var embedded = new CurrentIdentityApplicationService(currentUserService, "42");
        when(currentUserService.findCurrentUserDetails()).thenReturn(mock(UserDetails.class));

        assertThatThrownBy(embedded::requireApplicationUniqueId)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Application authentication is required");
    }

    @Test
    void requireApplicationPrincipal_whenApiKeyApplicationIsAuthenticated_returnsPrincipal() {
        var principal = mock(IdentityApplicationPrincipal.class);
        when(currentUserService.findCurrentUserDetails()).thenReturn(principal);

        assertThat(service.requireApplicationPrincipal()).isSameAs(principal);
    }

    @Test
    void requireApplicationPrincipal_whenNonApplicationPrincipalIsAuthenticated_denies() {
        when(currentUserService.findCurrentUserDetails()).thenReturn(mock(UserDetails.class));

        assertThatThrownBy(service::requireApplicationPrincipal)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Application authentication is required");
    }

    @Test
    void requireApplicationPrincipal_whenNoPrincipalExists_deniesWithoutUsingEmbeddedFallback() {
        var embedded = new CurrentIdentityApplicationService(currentUserService, "42");

        assertThatThrownBy(embedded::requireApplicationPrincipal)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Application authentication is required");
    }
}
