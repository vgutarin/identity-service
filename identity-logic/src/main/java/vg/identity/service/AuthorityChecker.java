package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vg.identity.model.IdentityUserSystemRole;
import vg.identity.repository.IdentityRoleAssignmentRepository;

import java.util.Collections;
import java.util.List;

@Service("authorityChecker")
@Slf4j
@RequiredArgsConstructor
public class AuthorityChecker {

    private final CurrentUserService userService;
    private final IdentityRoleAssignmentRepository roleAssignmentRepository;
    private final IdentityWorkspaceService workspaceService;
    private final IdentityApplicationService applicationService;

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


    /**
     * Global authority check.
     * Global owners can do anything.
     *
     * @param permission permission to check
     * @return true if the current user has global authority
     */
    public boolean hasAuthority(String permission) {

        var currentUserDetails = userService.getCurrentUserDetails();
        if (currentUserDetails == null) {
            return false;
        }

        return userService.hasRole(IdentityUserSystemRole.OWNER.name());
    }


    /**
     * Scope authority check.
     * Hierarchical authority check.
     * If the current user has authority within the given access scope resource, return true.
     * If the current user has authority within the parent access scope resource, return true.
     * If the current user has global authority, return true.
     * Otherwise, return false.
     *
     * @param accessScopeResourceUniqueId access scope resource unique id
     * @param permission permission to check
     * @return true if the current user has authority withing the given access scope resource
     */
    public boolean hasAuthority(long accessScopeResourceUniqueId, String permission) {

        if (hasAuthority(permission)) {
            return true;
        }

        var currentUserUniqueId = userService.getCurrentUserUniqueId();
        if (currentUserUniqueId == null) {
            return false;
        }

        // workspace is the highest scope level (below global)
        // any given access scope resource eventually belongs to a workspace
        var pathToWorkspace = pathToWorkspace(accessScopeResourceUniqueId);
        if (pathToWorkspace.isEmpty()) {
            log.error(
                    "Could not find workspace for accessScopeResourceUniqueId {}. Permission {}",
                    accessScopeResourceUniqueId,
                    permission
            );
            return false;
        }

        return roleAssignmentRepository.hasPermission(currentUserUniqueId.value(), pathToWorkspace, permission);
    }

    private List<Long> pathToWorkspace(long accessScopeResourceUniqueId) {

        if (workspaceService.existsById(accessScopeResourceUniqueId)) {
            return List.of(accessScopeResourceUniqueId);
        }

        var res = applicationService.findById(accessScopeResourceUniqueId);
        if (null == res) {
            log.warn("Given accessScopeResourceUniqueId {} is neither a workspace not an application.", accessScopeResourceUniqueId);
            return Collections.emptyList();
        }

        return List.of(
                res.getUniqueId().value(),
                res.getWorkspaceUniqueId()
        );
    }
}
