package vg.identity.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vg.identity.model.User;
import vg.identity.service.UserService;
import vg.identity.service.UserServiceImpl;

import java.util.Collection;

@RequiredArgsConstructor
@RestController
@RequestMapping("user")
public class UserController implements UserService {

    private final UserServiceImpl logic;

    @Override
    @PostMapping("create")
    public User create(@RequestBody User user) {
        return logic.create(user);
    }

    @Override
    @PutMapping("update")
    public User update(@RequestBody User user) {
        return logic.update(user);
    }

    @GetMapping("all")
    @Override
    public Collection<User> getAll() {
        return logic.getAll();
    }
}
