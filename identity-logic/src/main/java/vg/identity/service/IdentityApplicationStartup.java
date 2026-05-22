package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;
import vg.identity.model.IdentityUser;

@Slf4j
@RequiredArgsConstructor
@Component
public class IdentityApplicationStartup {

    private final UserDetailsManager userDetailsManager;
    private final IdentityUserServiceImpl userService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Identity application started");
        userDetailsManager.createUser(
                userService.getGuest()
        );
        createUser("g", "g");
        createUser("a", "a");
    }

    private void createUser(String username, String psw) {
        userDetailsManager.createUser(
                userService.create(
                        IdentityUser.builder()
                                .username(username)
                                .password(psw)
                                .build()
                )
        );
    }
}
