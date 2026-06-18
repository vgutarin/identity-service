package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorityCheckerTest {

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private AuthorityChecker authorityChecker;

    @Test
    void hasResourceAuthority_shouldReturnTrue_whenUserHasResourceAuthority() {
        var resourceUniqueId = 123L;
        var permission = " Read ";
        var userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("123:read")));
        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);

        assertThat(
                authorityChecker.hasResourceAuthority(resourceUniqueId, permission)
        ).isTrue();
    }

    @Test
    void hasResourceAuthority_shouldReturnTrue_whenUserIsOwner() {
        var userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);

        assertThat(
                authorityChecker.hasResourceAuthority(123L, "read")
        ).isTrue();
    }

    @Test
    void hasResourceAuthority_shouldReturnFalse_whenUserDoesNotHaveResourceAuthority() {
        var userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("123:write")));
        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);

        // Then
        assertThat(
                authorityChecker.hasResourceAuthority(123L, "read")
        ).isFalse();
    }
}