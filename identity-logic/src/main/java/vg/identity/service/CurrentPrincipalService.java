package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
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
public class CurrentPrincipalService {

    private final IdentityUserService userService;

    /** Returns the current principal as Spring user details, or null when it cannot be resolved. */
    public UserDetails findCurrentPrincipal() {
        return getCurrentAuthentication()
                .map(Authentication::getPrincipal)
                .filter(UserDetails.class::isInstance)
                .map(UserDetails.class::cast)
                .orElse(null);
    }

    public UniqueId findCurrentPrincipalUniqueId() {
        var currentPrincipal = findCurrentPrincipal();
        if (null == currentPrincipal) {
            return null;
        }

        if (currentPrincipal instanceof Identifiable identifiable) {
            return identifiable.getUniqueId();
        }

        return userService.findUniqueIdByUsername(currentPrincipal.getUsername());
    }

    /** Returns the resolved current principal identifier, or denies access when it is absent. */
    public UniqueId requirePrincipalUniqueId() {
        var uniqueId = findCurrentPrincipalUniqueId();
        if (uniqueId == null) {
            throw new AccessDeniedException("An authenticated principal identifier is required");
        }
        return uniqueId;
    }

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
