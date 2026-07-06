package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vg.identity.model.IdentityUserSystemRole;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityRoleAssignmentRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.model.UniqueId;

import java.util.Collections;
import java.util.List;

@Service("authorityChecker")
@Slf4j
@RequiredArgsConstructor
public class AuthorityChecker {

    private final CurrentUserService userService;

    // AuthorityChecker is invoked by @PreAuthorize expressions on service methods.
    // Use repositories directly to avoid calling secured services from inside authorization checks,
    // which can cause circular authorization/service dependencies.
    private final IdentityRoleAssignmentRepository roleAssignmentRepository;
    private final IdentityWorkspaceRepository workspaceRepository;
    private final IdentityApplicationRepository applicationRepository;

    @Deprecated
    public boolean hasResourceAuthority(long resourceUniqueId, String permission) {

        var currentUserDetails = userService.findCurrentUserDetails();
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

        var currentUserDetails = userService.findCurrentUserDetails();
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
    public boolean hasAuthority(UniqueId accessScopeResourceUniqueId, String permission) {

        if (hasAuthority(permission)) {
            return true;
        }

        var currentUserUniqueId = userService.findCurrentUserUniqueId();
        if (currentUserUniqueId == null) {
            return false;
        }

        // workspace is the highest scope level (below global)
        // any given access scope resource eventually belongs to a workspace
        var pathToWorkspace = pathToWorkspace(accessScopeResourceUniqueId);
        if (pathToWorkspace.isEmpty()) {
            log.error(
                    "Could not find workspace for accessScopeResourceUniqueId {}. Permission {}",
                    accessScopeResourceUniqueId.getLongValue(),
                    permission
            );
            return false;
        }

        return roleAssignmentRepository.hasPermission(currentUserUniqueId.getLongValue(), pathToWorkspace, permission);
    }

    private List<Long> pathToWorkspace(UniqueId accessScopeResourceUniqueId) {


        if (workspaceRepository.existsById(accessScopeResourceUniqueId.getLongValue())) {
            return List.of(accessScopeResourceUniqueId.getLongValue());
        }

        var res = applicationRepository.findById(accessScopeResourceUniqueId).orElse(null);
        if (null == res) {
            log.warn("Given accessScopeResourceUniqueId {} is neither a workspace not an application.", accessScopeResourceUniqueId);
            return Collections.emptyList();
        }

        return List.of(
                res.getUniqueId(),
                res.getWorkspace().getUniqueId()
        );
    }
}
