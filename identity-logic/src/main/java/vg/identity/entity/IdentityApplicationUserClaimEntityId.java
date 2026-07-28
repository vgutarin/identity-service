package vg.identity.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class IdentityApplicationUserClaimEntityId implements Serializable {
    private Long application;
    private Long identityUser;
    private Integer scope;
    private Integer claim;
}
