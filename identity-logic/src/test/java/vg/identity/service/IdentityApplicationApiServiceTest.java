package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetails;
import vg.identity.model.IdentityApplication;
import vg.identity.model.IdentityApplicationPrincipal;
import vg.unique.id.model.UniqueId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityApplicationApiServiceTest {
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private IdentityApplicationService applicationService;

    private IdentityApplicationApiService service;

    @BeforeEach
    void setUp() {
        service = new IdentityApplicationApiService(currentUserService, applicationService);
    }

    @Test
    void me_whenCurrentPrincipalIsApplication_returnsOnlySafeApplicationMetadata() {
        var uniqueId = new UniqueId(42L);
        var principal = mock(IdentityApplicationPrincipal.class);
        when(principal.getUniqueId()).thenReturn(uniqueId);
        when(currentUserService.findCurrentUserDetails()).thenReturn(principal);
        when(applicationService.getAuthenticatedApplication(uniqueId)).thenReturn(
                IdentityApplication.builder()
                        .uniqueId(uniqueId)
                        .workspaceUniqueId(7L)
                        .name("Orders")
                        .uri("https://example.test/orders")
                        .payload("must-not-be-returned")
                        .build()
        );

        var response = service.me();

        assertThat(response.uniqueId()).isEqualTo("42");
        assertThat(response.workspaceUniqueId()).isEqualTo(7L);
        assertThat(response.name()).isEqualTo("Orders");
        assertThat(response.uri()).isEqualTo("https://example.test/orders");
        assertThat(response.getClass().getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("uniqueId", "workspaceUniqueId", "name", "uri");
    }

    @Test
    void me_whenCurrentPrincipalIsNotApplication_throwsAccessDeniedException() {
        when(currentUserService.findCurrentUserDetails()).thenReturn(mock(UserDetails.class));

        assertThatThrownBy(service::me)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Application authentication is required");

        verifyNoInteractions(applicationService);
    }
}
