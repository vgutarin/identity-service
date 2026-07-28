package vg.identity.entity;

import lombok.Data;

import java.io.Serializable;

@Data
public class IdentityApplicationUserEntityId implements Serializable {
    private Long application;
    private Long identityUser;
}
