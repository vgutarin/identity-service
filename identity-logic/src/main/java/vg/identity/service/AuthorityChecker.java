package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vg.identity.model.IdentityUserSystemRole;
import vg.identity.repository.IdentityRoleAssignmentRepository;
import vg.identity.repository.IdentityWorkspaceRepository;

@Service("authorityChecker")
@Slf4j
@RequiredArgsConstructor
public class AuthorityChecker {

    private final CurrentUserService userService;
//    private final IdentityRoleAssignmentRepository roleAssignmentRepository;
//    private final IdentityWorkspaceRepository workspaceRepository;
//
//    public boolean hasAuthority(String permission) {
//
//        var currentUserDetails = userService.getCurrentUserDetails();
//        if (currentUserDetails == null) {
//            return false;
//        }
//
//        if (userService.hasRole(IdentityUserSystemRole.OWNER.name())) {
//            // global owner can do anything
//            return true;
//        }
//        roleAssignmentRepository.hasGlobalPermission
//    }
//
//    public boolean hasAuthority(long accessScopeResourceUniqueId, String permission) {
//
//        var currentUserDetails = userService.getCurrentUserDetails();
//        if (currentUserDetails == null) {
//            return false;
//        }
//
//        var workspaceId = (Long) null;
//
//
//        if (userService.hasRole(IdentityUserSystemRole.OWNER.name())) {
//            // global owner can do anything
//            return true;
//        }
//    }

    @Deprecated
    public boolean hasResourceAuthority(long resourceUniqueId, String permission) {

        var currentUserDetails = userService.getCurrentUserDetails();
        if (currentUserDetails == null) {
            return false;
        }

        var normalizedRole = IdentityUserAuthorityService.normalizeRoleName(
                IdentityUserSystemRole.OWNER.name()
        );

        var normalizedPermission = IdentityUserAuthorityService.resourceAuthorityName(resourceUniqueId, permission);
        return currentUserDetails.getAuthorities()
                .stream()
                .anyMatch(grantedAuthority ->
                        normalizedRole.equals(grantedAuthority.getAuthority()) || normalizedPermission.equals(grantedAuthority.getAuthority())
                );
    }

//    private Long findWorkspaceId(long accessScopeResourceUniqueId) {
//        if (workspaceRepository.existsById(accessScopeResourceUniqueId)) {
//            return accessScopeResourceUniqueId;
//        }
//
//        log.warn("Given accessScopeResourceUniqueId {} is not a workspace.", accessScopeResourceUniqueId);
//        return null;
//
//    }
}
