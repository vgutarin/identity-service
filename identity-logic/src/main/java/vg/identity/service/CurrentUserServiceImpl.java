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
}
