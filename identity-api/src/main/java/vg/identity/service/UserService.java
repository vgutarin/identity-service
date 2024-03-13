package vg.identity.service;


import vg.identity.model.User;

import java.util.Collection;

//TODO consider do we need this methods
public interface UserService {
    User create(User user);
    User update(User user);
    Collection<User> getAll();
}
