package vg.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceImplTest {

    @Mock
    private IdentityUserService userService;

    @InjectMocks
    private CurrentUserServiceImpl currentUserService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findCurrentUserDetails_whenAuthenticated_returnsUserDetails() {
        var userDetails = mock(UserDetails.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        var result = currentUserService.findCurrentUserDetails();

        assertThat(result).isEqualTo(userDetails);
    }

    @Test
    void findCurrentUserDetails_whenNotAuthenticated_returnsNull() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        var result = currentUserService.findCurrentUserDetails();

        assertThat(result).isNull();
    }

    @Test
    void findCurrentUserDetails_whenNoAuthentication_returnsNull() {
        when(securityContext.getAuthentication()).thenReturn(null);

        var result = currentUserService.findCurrentUserDetails();

        assertThat(result).isNull();
    }

    @Test
    void findCurrentUserDetails_whenPrincipalIsNotUserDetails_returnsNull() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        var result = currentUserService.findCurrentUserDetails();

        assertThat(result).isNull();
    }

    @Test
    void findCurrentUserUniqueId_whenPrincipalIsIdentifiable_returnsUniqueId() {
        var uniqueId = new UniqueId(123L);
        var identifiableUserDetails = mock(IdentifiableUserDetails.class);
        when(identifiableUserDetails.getUniqueId()).thenReturn(uniqueId);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(identifiableUserDetails);

        var result = currentUserService.findCurrentUserUniqueId();

        assertThat(result).isEqualTo(uniqueId);
    }

    @Test
    void findCurrentUserUniqueId_whenPrincipalIsNotIdentifiable_returnsUniqueIdFromUserService() {
        var uniqueId = new UniqueId(123L);
        var username = "john";
        var userDetails = mock(UserDetails.class);
        when(userDetails.getUsername()).thenReturn(username);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userService.findUniqueIdByUsername(username)).thenReturn(uniqueId);

        var result = currentUserService.findCurrentUserUniqueId();

        assertThat(result).isEqualTo(uniqueId);
    }

    @Test
    void findCurrentUserUniqueId_whenNotAuthenticated_returnsNull() {
        when(securityContext.getAuthentication()).thenReturn(null);

        var result = currentUserService.findCurrentUserUniqueId();

        assertThat(result).isNull();
    }

    @Test
    void hasRole_whenAuthenticationHasRole_returnsTrue() {
        var role = "ADMIN";
        var expectedAuthority = "ROLE_ADMIN";

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority(expectedAuthority)));

        var result = currentUserService.hasRole(role);

        assertThat(result).isTrue();
    }

    @Test
    void hasRole_whenAuthenticationHasRole_returnsTrueWithPrefix() {
        var role = "ROLE_ADMIN";
        var expectedAuthority = "ROLE_ADMIN";
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority(expectedAuthority)));

        var result = currentUserService.hasRole(role);

        assertThat(result).isTrue();
    }

    @Test
    void hasRole_whenAuthenticationDoesNotHaveRole_returnsFalse() {
        var role = "ADMIN";
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("ROLE_USER")));

        var result = currentUserService.hasRole(role);

        assertThat(result).isFalse();
    }

    @Test
    void hasRole_whenRoleCaseDiffers_returnsTrue() {
        var role = "admin";
        var expectedAuthority = "ROLE_ADMIN";
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority(expectedAuthority)));

        var result = currentUserService.hasRole(role);

        assertThat(result).isTrue();
    }

    @Test
    void hasRole_whenNotAuthenticated_returnsFalse() {
        when(securityContext.getAuthentication()).thenReturn(null);

        var result = currentUserService.hasRole("ADMIN");

        assertThat(result).isFalse();
    }

    private interface IdentifiableUserDetails extends UserDetails, Identifiable {}
}
