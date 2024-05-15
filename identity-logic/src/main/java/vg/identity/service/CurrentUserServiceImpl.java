package vg.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import vg.identity.model.User;
import vg.identity.repository.UserRepository;
import vg.unique.id.model.UniqueId;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CurrentUserServiceImpl implements CurrentUserService {
    private final UserRepository userRepository;
    private final User anonymous;

    @Override
    public UserDetails getCurrentUserDetails() {

        return Optional.ofNullable(SecurityContextHolder.getContext())
                .map(SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .map(UserDetails.class::cast)
                .orElse(anonymous);
    }

    @Override
    public UniqueId getCurrentUserUniqueId() {
        var currentUserDetails = getCurrentUserDetails();
        if (anonymous == currentUserDetails) {
            return anonymous.getUniqueId();
        }
        return new UniqueId(userRepository.findByUsername(currentUserDetails.getUsername()).getUniqueId());
    }
}
