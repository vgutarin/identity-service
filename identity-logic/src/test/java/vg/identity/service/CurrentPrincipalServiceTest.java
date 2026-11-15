package vg.identity.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import vg.identity.model.principal.IdentityInternalApplicationPrincipal;
import vg.identity.model.principal.IdentityInternalUser;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentPrincipalServiceTest {

    @Mock
    private IdentityUserService userService;

    private CurrentPrincipalService currentPrincipalService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @Mock
    private UserDetails userDetails;

    @Mock
    private IdentifiableUserDetails identifiableUserDetails;

    @Mock
    private IdentityInternalApplicationPrincipal applicationPrincipal;

    @BeforeEach
    void setUp() {
        currentPrincipalService = new CurrentPrincipalService(userService);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findCurrentPrincipal_whenAuthenticated_returnsUserDetails() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        var result = currentPrincipalService.findCurrentPrincipal();

        assertThat(result).isEqualTo(userDetails);
    }

    @Test
    void findCurrentPrincipal_whenNotAuthenticated_returnsNull() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        var result = currentPrincipalService.findCurrentPrincipal();

        assertThat(result).isNull();
    }

    @Test
    void findCurrentPrincipal_whenNoAuthentication_returnsNull() {
        when(securityContext.getAuthentication()).thenReturn(null);

        var result = currentPrincipalService.findCurrentPrincipal();

        assertThat(result).isNull();
    }

    @Test
    void findCurrentPrincipal_whenPrincipalIsNotUserDetails_returnsNull() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("anonymousUser");

        var result = currentPrincipalService.findCurrentPrincipal();

        assertThat(result).isNull();
    }

    @Test
    void findCurrentPrincipalUniqueId_whenPrincipalIsIdentifiable_returnsUniqueId() {
        var uniqueId = new UniqueId(123L);
        when(identifiableUserDetails.getUniqueId()).thenReturn(uniqueId);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(identifiableUserDetails);

        var result = currentPrincipalService.findCurrentPrincipalUniqueId();

        assertThat(result).isEqualTo(uniqueId);
    }

    @Test
    void findCurrentPrincipalUniqueId_whenPrincipalIsNotIdentifiable_returnsUniqueIdFromUserService() {
        var uniqueId = new UniqueId(123L);
        var username = "john";
        when(userDetails.getUsername()).thenReturn(username);

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userService.findUniqueIdByUsername(username)).thenReturn(uniqueId);

        var result = currentPrincipalService.findCurrentPrincipalUniqueId();

        assertThat(result).isEqualTo(uniqueId);
    }

    @Test
    void findCurrentPrincipalUniqueId_whenNotAuthenticated_returnsNull() {
        when(securityContext.getAuthentication()).thenReturn(null);

        var result = currentPrincipalService.findCurrentPrincipalUniqueId();

        assertThat(result).isNull();
    }

    @Test
    void requirePrincipalUniqueId_whenAuthenticatedIdentityUserHasIdentifier_returnsIdentifierWithoutLookup() {
        var uniqueId = new UniqueId(123L);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(IdentityInternalUser.builder().uniqueId(uniqueId).build());

        assertThat(currentPrincipalService.requirePrincipalUniqueId()).isEqualTo(uniqueId);

        verifyNoInteractions(userService);
    }

    @ParameterizedTest
    @MethodSource("invalidUserPrincipals")
    void requirePrincipalUniqueId_whenPrincipalHasNoResolvableIdentifier_deniesAccessWithoutLookup(Object principal) {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(principal);

        assertPrincipalRequired();
    }

    @Test
    void requirePrincipalUniqueId_whenApplicationHasIdentifier_returnsIdentifierWithoutLookup() {
        var uniqueId = new UniqueId(123L);
        when(applicationPrincipal.getUniqueId()).thenReturn(uniqueId);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(applicationPrincipal);

        assertThat(currentPrincipalService.requirePrincipalUniqueId()).isEqualTo(uniqueId);

        verifyNoInteractions(userService);
    }

    @Test
    void requirePrincipalUniqueId_whenApplicationHasNoIdentifier_deniesAccessWithoutLookup() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(applicationPrincipal);

        assertPrincipalRequired();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requirePrincipalUniqueId_whenPrincipalRequiresUsernameLookup_returnsIdentifierOrDeniesMissingResult(boolean found) {
        var uniqueId = found ? new UniqueId(123L) : null;
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(userDetails.getUsername()).thenReturn("resolved-user");
        when(userService.findUniqueIdByUsername("resolved-user")).thenReturn(uniqueId);

        if (found) {
            assertThat(currentPrincipalService.requirePrincipalUniqueId()).isEqualTo(uniqueId);
        } else {
            assertThatThrownBy(currentPrincipalService::requirePrincipalUniqueId)
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("An authenticated principal identifier is required");
        }
    }

    @Test
    void requirePrincipalUniqueId_whenNotAuthenticated_deniesAccessWithoutLookup() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(false);

        assertPrincipalRequired();
    }

    @Test
    void requirePrincipalUniqueId_whenNoAuthentication_deniesAccessWithoutLookup() {
        when(securityContext.getAuthentication()).thenReturn(null);

        assertPrincipalRequired();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void requirePrincipalUniqueId_whenAuthenticationIsAnonymous_usesExistingIdentifierResolution(boolean identityUser) {
        var principal = identityUser ? IdentityInternalUser.builder().uniqueId(new UniqueId(123L)).build() : "anonymousUser";
        var anonymous = new AnonymousAuthenticationToken("test", principal,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        when(securityContext.getAuthentication()).thenReturn(anonymous);
        assertThat(anonymous.isAuthenticated()).isTrue();

        if (identityUser) {
            assertThat(currentPrincipalService.requirePrincipalUniqueId()).isEqualTo(new UniqueId(123L));
            verifyNoInteractions(userService);
        } else {
            assertPrincipalRequired();
        }
    }

    private void assertPrincipalRequired() {
        assertThatThrownBy(currentPrincipalService::requirePrincipalUniqueId)
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("An authenticated principal identifier is required");
        verifyNoInteractions(userService);
    }

    private static Stream<Object> invalidUserPrincipals() {
        return Stream.of(null, "anonymousUser", IdentityInternalUser.builder().username("bootstrap").build());
    }

    @Test
    void hasRole_whenAuthenticationHasRole_returnsTrue() {
        var role = "ADMIN";
        var expectedAuthority = "ROLE_ADMIN";

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority(expectedAuthority)));

        var result = currentPrincipalService.hasRole(role);

        assertThat(result).isTrue();
    }

    @Test
    void hasRole_whenAuthenticationHasRole_returnsTrueWithPrefix() {
        var role = "ROLE_ADMIN";
        var expectedAuthority = "ROLE_ADMIN";

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority(expectedAuthority)));

        var result = currentPrincipalService.hasRole(role);

        assertThat(result).isTrue();
    }

    @Test
    void hasRole_whenAuthenticationDoesNotHaveRole_returnsFalse() {
        var role = "ADMIN";

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("ROLE_USER")));

        var result = currentPrincipalService.hasRole(role);

        assertThat(result).isFalse();
    }

    @Test
    void hasRole_whenRoleCaseDiffers_returnsTrue() {
        var role = "admin";
        var expectedAuthority = "ROLE_ADMIN";

        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority(expectedAuthority)));

        var result = currentPrincipalService.hasRole(role);

        assertThat(result).isTrue();
    }

    @Test
    void hasRole_whenNotAuthenticated_returnsFalse() {
        when(securityContext.getAuthentication()).thenReturn(null);

        var result = currentPrincipalService.hasRole("ADMIN");

        assertThat(result).isFalse();
    }

    private interface IdentifiableUserDetails extends UserDetails, Identifiable {}
}
