package vg.identity.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vg.unique.id.Identifiable;
import vg.unique.id.model.UniqueId;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Represents user
 */

//TODO implement
@Data
@Builder
public class User implements Identifiable, UserDetails {

    private UniqueId uniqueId;

    private String username;
    private String password;

    private Instant createdAtTime;
    private int version;


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
