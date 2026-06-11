package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import vg.identity.model.IdentityUserSystemRole;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

@Service("authorityChecker")
@RequiredArgsConstructor
public class CurrentUserServiceImpl implements CurrentUserService {

    private final IdentityUserServiceImpl userService;

    @Override
    public UserDetails getCurrentUserDetails() {
        return Optional.of(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .map(UserDetails.class::cast)
                .orElse(userService.getGuest());
    }

    @Override
    public UniqueId getCurrentUserUniqueId() {
        var currentUserDetails = getCurrentUserDetails();
        var guest = userService.getGuest();
        if (guest == currentUserDetails) {
            return guest.getUniqueId();
        }
        return  userService.findByUsername(currentUserDetails.getUsername()).getUniqueId();
    }

    @Override
    public boolean hasRole(String role) {
        var currentUserDetails = getCurrentUserDetails();
        var normalizedRole = IdentityUserAuthorityService.normalizeRoleName(role);
        return currentUserDetails != null && currentUserDetails.getAuthorities().stream()
                .anyMatch(grantedAuthority -> normalizedRole.equals(grantedAuthority.getAuthority()));
    }

    public boolean hasResourceAuthority(long resourceUniqueId, String permission) {

        var currentUserDetails = getCurrentUserDetails();
        if (currentUserDetails == null) {
            return false;
        }
        var normalizedRole = IdentityUserAuthorityService.normalizeRoleName(
                IdentityUserSystemRole.IDENTITY_ADMIN.name()
        );
        var normalizedPermission = IdentityUserAuthorityService.resourceAuthorityName(resourceUniqueId, permission);
        return currentUserDetails.getAuthorities()
                .stream()
                .anyMatch(grantedAuthority ->
                        normalizedRole.equals(grantedAuthority.getAuthority()) || normalizedPermission.equals(grantedAuthority.getAuthority())
                );
    }
}
