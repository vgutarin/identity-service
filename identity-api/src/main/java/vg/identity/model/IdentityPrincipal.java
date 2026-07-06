package vg.identity.model;

import org.springframework.security.core.userdetails.UserDetails;
import vg.unique.id.Identifiable;

public interface IdentityPrincipal extends Identifiable, UserDetails {
}
