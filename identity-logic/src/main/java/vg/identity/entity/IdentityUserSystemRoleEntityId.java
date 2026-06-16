package vg.identity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.identity.model.IdentityUserSystemRole;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityUserSystemRoleEntityId implements Serializable {
    private Long identityPrincipalUniqueId;
    private IdentityUserSystemRole role;
}
