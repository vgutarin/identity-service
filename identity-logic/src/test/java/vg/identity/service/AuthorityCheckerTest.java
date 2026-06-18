package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vg.identity.model.IdentityApplication;
import vg.identity.repository.IdentityRoleAssignmentRepository;
import vg.unique.id.model.UniqueId;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static vg.test.TestHelper.nextLong;
import static vg.test.TestHelper.nextString;

@ExtendWith(MockitoExtension.class)
class AuthorityCheckerTest {

    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private IdentityRoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private IdentityWorkspaceService workspaceService;
    @Mock
    private IdentityApplicationService applicationService;

    @InjectMocks
    private AuthorityChecker authorityChecker;

    @Test
    void hasResourceAuthority_whenUserHasResourceAuthority_returnsTrue() {
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
    void hasResourceAuthority_whenUserIsOwner_returnsTrue() {
        var userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);

        assertThat(
                authorityChecker.hasResourceAuthority(123L, "read")
        ).isTrue();
    }

    @Test
    void hasResourceAuthority_whenUserDoesNotHaveResourceAuthority_returnsFalse() {
        var userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("123:write")));
        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);

        // Then
        assertThat(
                authorityChecker.hasResourceAuthority(123L, "read")
        ).isFalse();
    }

    @Test
    void hasAuthority_whenCurrentUserDetailsAreMissing_returnsFalse() {
        var permission = nextString();

        when(currentUserService.getCurrentUserDetails()).thenReturn(null);

        assertThat(authorityChecker.hasAuthority(permission)).isFalse();
    }

    @Test
    void hasAuthority_whenUserIsOwner_returnsTrue() {
        var permission = nextString();
        var userDetails = mock(UserDetails.class);

        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(true);

        assertThat(authorityChecker.hasAuthority(permission)).isTrue();
    }

    @Test
    void hasAuthority_whenUserIsNotOwner_returnsFalse() {
        var permission = nextString();
        var userDetails = mock(UserDetails.class);

        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);

        assertThat(authorityChecker.hasAuthority(permission)).isFalse();
    }

    @Test
    void hasAuthority_withScopeAndUserIsOwner_returnsTrue() {
        var userDetails = mock(UserDetails.class);

        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(true);

        assertThat(authorityChecker.hasAuthority(nextLong(), nextString())).isTrue();
        verifyNoInteractions(roleAssignmentRepository);
    }

    @Test
    void hasAuthority_withScopeAndCurrentUserUniqueIdIsMissing_returnsFalse() {
        var userDetails = mock(UserDetails.class);

        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);
        when(currentUserService.getCurrentUserUniqueId()).thenReturn(null);

        assertThat(authorityChecker.hasAuthority(nextLong(), nextString())).isFalse();
    }

    @Test
    void hasAuthority_withWorkspaceScopeAndRepositoryGrantsPermission_returnsTrue() {
        var userDetails = mock(UserDetails.class);
        var userUniqueId = new UniqueId(nextLong());
        var workspaceId = nextLong();
        var permission = nextString();

        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);
        when(currentUserService.getCurrentUserUniqueId()).thenReturn(userUniqueId);
        when(workspaceService.existsById(workspaceId)).thenReturn(true);
        when(roleAssignmentRepository.hasPermission(userUniqueId.value(), List.of(workspaceId), permission)).thenReturn(true);

        assertThat(authorityChecker.hasAuthority(workspaceId, permission)).isTrue();
    }

    @Test
    void hasAuthority_withApplicationScopeAndRepositoryGrantsPermissionOnPath_returnsTrue() {
        var userDetails = mock(UserDetails.class);
        var userUniqueId = new UniqueId(nextLong());
        var applicationId = nextLong();
        var workspaceId = nextLong();
        var permission = nextString();

        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);
        when(currentUserService.getCurrentUserUniqueId()).thenReturn(userUniqueId);
        when(workspaceService.existsById(applicationId)).thenReturn(false);
        when(applicationService.findById(applicationId)).thenReturn(IdentityApplication.builder()
                .uniqueId(new UniqueId(applicationId))
                .workspaceUniqueId(workspaceId)
                .build());
        when(roleAssignmentRepository.hasPermission(
                userUniqueId.value(),
                List.of(applicationId, workspaceId),
                permission
        )).thenReturn(true);

        assertThat(authorityChecker.hasAuthority(applicationId, permission)).isTrue();
    }

    @Test
    void hasAuthority_withScopeAndResourceDoesNotBelongToSupportedScope_returnsFalse() {
        var userDetails = mock(UserDetails.class);
        var userUniqueId = new UniqueId(nextLong());
        var resourceId = nextLong();

        when(currentUserService.getCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);
        when(currentUserService.getCurrentUserUniqueId()).thenReturn(userUniqueId);
        when(workspaceService.existsById(resourceId)).thenReturn(false);
        when(applicationService.findById(resourceId)).thenReturn(null);

        assertThat(authorityChecker.hasAuthority(resourceId, nextString())).isFalse();
        verifyNoInteractions(roleAssignmentRepository);
    }
}
