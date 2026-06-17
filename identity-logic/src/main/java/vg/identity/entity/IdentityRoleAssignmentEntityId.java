package vg.identity.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vg.identity.model.access.AccessScope;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityRoleAssignmentEntityId implements Serializable {
    private Long principal;
    private Long role;
    private AccessScope accessScope;
}
