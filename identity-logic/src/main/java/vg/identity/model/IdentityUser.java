package vg.identity.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.Collection;

/**
 * Represents user
 */

//TODO implement
@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class IdentityUser implements IdentityPrincipal {

    private UniqueId uniqueId;

    private String username;
    private String password;
    private Collection<? extends GrantedAuthority> authorities;

    private Instant createdAt;
    private int version;


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
