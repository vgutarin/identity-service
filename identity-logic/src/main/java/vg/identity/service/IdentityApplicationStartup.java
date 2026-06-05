package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityUserSystemRole;

@Slf4j
@RequiredArgsConstructor
@Component
public class IdentityApplicationStartup {

    private final UserDetailsManager userDetailsManager;
    private final IdentityUserServiceImpl userService;
    private final IdentityUserAuthorityService authorityService;

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationReady() {
        log.info("Identity application started");
        userDetailsManager.createUser(
                userService.getGuest()
        );
        createUser("vg", "vg", IdentityUserSystemRole.IDENTITY_ADMIN);
        createUser("g", "g", null);
        createUser("a", "a", null);
    }

    private void createUser(String username, String psw, IdentityUserSystemRole role) {

        var user = userService.create(
                IdentityUser.builder()
                        .username(username)
                        .password(psw)
                        .build()
        );

        if (null != role) {
            authorityService.assignAuthorityTmpInsecure(user, role);
        }

        authorityService.loadAuthorities(user);
        userDetailsManager.createUser(
            user
        );
        log.info("Created user: {}", username);
    }
}
