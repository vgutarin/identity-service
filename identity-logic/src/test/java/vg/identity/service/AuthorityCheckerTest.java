package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityRoleAssignmentRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.model.UniqueId;

import java.util.List;
import java.util.Optional;

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
    private IdentityWorkspaceRepository workspaceRepository;
    @Mock
    private IdentityApplicationRepository applicationRepository;

    @InjectMocks
    private AuthorityChecker authorityChecker;

    @Test
    void hasResourceAuthority_whenUserHasResourceAuthority_returnsTrue() {
        var resourceUniqueId = 123L;
        var permission = " Read ";
        var userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("123:read")));
        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);

        assertThat(
                authorityChecker.hasResourceAuthority(resourceUniqueId, permission)
        ).isTrue();
    }

    @Test
    void hasResourceAuthority_whenUserIsOwner_returnsTrue() {
        var userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);

        assertThat(
                authorityChecker.hasResourceAuthority(123L, "read")
        ).isTrue();
    }

    @Test
    void hasResourceAuthority_whenUserDoesNotHaveResourceAuthority_returnsFalse() {
        var userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenAnswer(invocation -> List.of(new SimpleGrantedAuthority("123:write")));
        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);

        // Then
        assertThat(
                authorityChecker.hasResourceAuthority(123L, "read")
        ).isFalse();
    }

    @Test
    void hasAuthority_whenCurrentUserDetailsAreMissing_returnsFalse() {
        var permission = nextString();

        when(currentUserService.findCurrentUserDetails()).thenReturn(null);

        assertThat(authorityChecker.hasAuthority(permission)).isFalse();
    }

    @Test
    void hasAuthority_whenUserIsOwner_returnsTrue() {
        var permission = nextString();
        var userDetails = mock(UserDetails.class);

        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(true);

        assertThat(authorityChecker.hasAuthority(permission)).isTrue();
    }

    @Test
    void hasAuthority_whenUserIsNotOwner_returnsFalse() {
        var permission = nextString();
        var userDetails = mock(UserDetails.class);

        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);

        assertThat(authorityChecker.hasAuthority(permission)).isFalse();
    }

    @Test
    void hasAuthority_withScopeAndUserIsOwner_returnsTrue() {
        var userDetails = mock(UserDetails.class);

        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(true);

        assertThat(authorityChecker.hasAuthority(new UniqueId(nextLong()), nextString())).isTrue();
        verifyNoInteractions(roleAssignmentRepository);
    }

    @Test
    void hasAuthority_withScopeAndCurrentUserUniqueIdIsMissing_returnsFalse() {
        var userDetails = mock(UserDetails.class);

        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);
        when(currentUserService.findCurrentUserUniqueId()).thenReturn(null);

        assertThat(authorityChecker.hasAuthority(new UniqueId(nextLong()), nextString())).isFalse();
    }

    @Test
    void hasAuthority_withWorkspaceScopeAndRepositoryGrantsPermission_returnsTrue() {
        var userDetails = mock(UserDetails.class);
        var userUniqueId = new UniqueId(nextLong());
        var workspaceUniqueId = new UniqueId(nextLong());
        var permission = nextString();

        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);
        when(currentUserService.findCurrentUserUniqueId()).thenReturn(userUniqueId);
        when(workspaceRepository.existsById(workspaceUniqueId.getLongValue())).thenReturn(true);
        when(
                roleAssignmentRepository.hasPermission(userUniqueId.getLongValue(), List.of(workspaceUniqueId.getLongValue()), permission)
        ).thenReturn(true);

        assertThat(authorityChecker.hasAuthority(workspaceUniqueId, permission)).isTrue();
    }

    @Test
    void hasAuthority_withApplicationScopeAndRepositoryGrantsPermissionOnPath_returnsTrue() {
        var userDetails = mock(UserDetails.class);
        var userUniqueId = new UniqueId(nextLong());
        var applicationUniqueId = new UniqueId(nextLong());
        var workspaceUniqueId = new UniqueId(nextLong());
        var permission = nextString();

        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);
        when(currentUserService.findCurrentUserUniqueId()).thenReturn(userUniqueId);
        when(workspaceRepository.existsById(applicationUniqueId.getLongValue())).thenReturn(false);
        when(applicationRepository.findById(applicationUniqueId)).thenReturn(Optional.of(IdentityApplicationEntity.builder()
                .uniqueId(applicationUniqueId.getLongValue())
                .workspace(IdentityWorkspaceEntity.builder()
                        .uniqueId(workspaceUniqueId.getLongValue())
                        .build())
                .build()));
        when(roleAssignmentRepository.hasPermission(
                userUniqueId.getLongValue(),
                List.of(applicationUniqueId.getLongValue(), workspaceUniqueId.getLongValue()),
                permission
        )).thenReturn(true);

        assertThat(authorityChecker.hasAuthority(applicationUniqueId, permission)).isTrue();
    }

    @Test
    void hasAuthority_withScopeAndResourceDoesNotBelongToSupportedScope_returnsFalse() {
        var userDetails = mock(UserDetails.class);
        var userUniqueId = new UniqueId(nextLong());
        var resourceUniqueId = new UniqueId(nextLong());

        when(currentUserService.findCurrentUserDetails()).thenReturn(userDetails);
        when(currentUserService.hasRole("OWNER")).thenReturn(false);
        when(currentUserService.findCurrentUserUniqueId()).thenReturn(userUniqueId);
        when(workspaceRepository.existsById(resourceUniqueId.getLongValue())).thenReturn(false);
        when(applicationRepository.findById(resourceUniqueId)).thenReturn(Optional.empty());

        assertThat(authorityChecker.hasAuthority(resourceUniqueId, nextString())).isFalse();
        verifyNoInteractions(roleAssignmentRepository);
    }
}
