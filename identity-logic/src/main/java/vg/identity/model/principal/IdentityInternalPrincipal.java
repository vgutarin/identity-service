package vg.identity.model.principal;

import org.springframework.security.core.userdetails.UserDetails;
import vg.unique.id.Identifiable;
//TODO VG consider to do not inherit UserDetails
public interface IdentityInternalPrincipal extends Identifiable, UserDetails {
}
