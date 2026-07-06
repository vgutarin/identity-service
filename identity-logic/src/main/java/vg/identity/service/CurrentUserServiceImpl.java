package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurrentUserServiceImpl implements CurrentUserService {

    private final IdentityUserService userService;

    @Override
    public UserDetails findCurrentUserDetails() {
        return getCurrentAuthentication()
                .map(Authentication::getPrincipal)
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .orElse(null);
    }

    @Override
    public UniqueId findCurrentUserUniqueId() {
        var currentUserDetails = findCurrentUserDetails();
        if (null == currentUserDetails) {
            return null;
        }

        if (currentUserDetails instanceof Identifiable identifiable) {
            return identifiable.getUniqueId();
        }

        return userService.findUniqueIdByUsername(currentUserDetails.getUsername());
    }

    @Override
    public boolean hasRole(String role) {
        return getCurrentAuthentication()
                .map(authentication -> {
                    var normalizedRole = IdentityUserAuthorityService.normalizeRoleName(role);
                    return authentication.getAuthorities().stream()
                            .anyMatch(grantedAuthority -> normalizedRole.equals(grantedAuthority.getAuthority()));
                })
                .orElse(false);

    }

    private Optional<Authentication> getCurrentAuthentication() {
        return Optional.of(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated);
    }
}
