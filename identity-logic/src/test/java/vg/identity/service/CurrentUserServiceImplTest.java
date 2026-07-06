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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserServiceImplTest {

    @Mock
    private IdentityUserServiceImpl userService;

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
    void hasRole_whenUserHasRole_returnsTrue() {
        // Given
        var role = "ADMIN";
        var expectedAuthority = "ROLE_ADMIN";
        var userDetails = mock(UserDetails.class);
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority(expectedAuthority)));

        // When
        var result = currentUserService.hasRole(role);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasRole_whenUserHasRole_returnsTrueWithPrefix() {
        // Given
        var role = "ROLE_ADMIN";
        var expectedAuthority = "ROLE_ADMIN";
        var userDetails = mock(UserDetails.class);
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority(expectedAuthority)));

        // When
        var result = currentUserService.hasRole(role);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void hasRole_whenUserDoesNotHaveRole_returnsFalse() {
        // Given
        var role = "ADMIN";
        var userDetails = mock(UserDetails.class);
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // When
        var result = currentUserService.hasRole(role);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void hasRole_whenRoleCaseDiffers_returnsTrue() {
        // Given
        var role = "admin";
        var expectedAuthority = "ROLE_ADMIN";
        var userDetails = mock(UserDetails.class);
        
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority(expectedAuthority)));

        // When
        var result = currentUserService.hasRole(role);

        // Then
        assertThat(result).isTrue();
    }

}
