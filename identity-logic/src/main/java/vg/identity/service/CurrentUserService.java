package vg.identity.service;

import org.springframework.security.core.userdetails.UserDetails;
import vg.unique.id.model.UniqueId;

public interface CurrentUserService {
    UserDetails findCurrentUserDetails();
    UniqueId findCurrentUserUniqueId();
    boolean hasRole(String role);
}
