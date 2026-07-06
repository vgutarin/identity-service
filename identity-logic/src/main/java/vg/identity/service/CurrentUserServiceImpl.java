package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurrentUserServiceImpl implements CurrentUserService {

    private final IdentityUserService userService;

    @Override
    public UserDetails findCurrentUserDetails() {
        return Optional.of(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .map(UserDetails.class::cast)
                .orElse(null);
    }

    @Override
    public UniqueId findCurrentUserUniqueId() {
        var currentUserDetails = findCurrentUserDetails();
        if (null == currentUserDetails) {
            return null;
        }
        return userService.findUniqueIdByUsername(currentUserDetails.getUsername());
    }

    @Override
    public boolean hasRole(String role) {
        var currentUserDetails = findCurrentUserDetails();
        var normalizedRole = IdentityUserAuthorityService.normalizeRoleName(role);
        return currentUserDetails != null && currentUserDetails.getAuthorities().stream()
                .anyMatch(grantedAuthority -> normalizedRole.equals(grantedAuthority.getAuthority()));
    }
}
