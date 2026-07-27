package vg.identity.model;

import org.springframework.security.core.GrantedAuthority;
import vg.unique.id.model.UniqueId;

import java.util.Collection;
import java.util.List;

/**
 * Application identity placed in the security context after an API key has been verified.
 * It contains no API-key material.
 */
public record IdentityApiKeyPrincipal(UniqueId uniqueId, String username) implements IdentityPrincipal {

    @Override
    public UniqueId getUniqueId() {
        return uniqueId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
