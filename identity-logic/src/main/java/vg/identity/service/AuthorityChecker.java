package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vg.identity.model.IdentityUserSystemRole;

@Service("authorityChecker")
@RequiredArgsConstructor
public class AuthorityChecker {

    private final CurrentUserService userService;

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
}
