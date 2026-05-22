package vg.identity.service;


import vg.identity.model.IdentityUser;

public interface IdentityUserService {
    IdentityUser create(IdentityUser user);

    IdentityUser update(IdentityUser user);
}
