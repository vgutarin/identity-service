package vg.identity.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vg.identity.model.IdentityUser;
import vg.identity.service.IdentityUserService;
import vg.identity.service.IdentityPrincipalService;

@RequiredArgsConstructor
@RestController
@RequestMapping("user")
public class IdentityUserController implements IdentityUserService {

    private final IdentityPrincipalService logic;

    @Override
    @PostMapping("create")
    public IdentityUser create(@RequestBody IdentityUser user) {
        return logic.create(user);
    }

    @Override
    @PutMapping("update")
    public IdentityUser update(@RequestBody IdentityUser user) {
        return logic.update(user);
    }
}
