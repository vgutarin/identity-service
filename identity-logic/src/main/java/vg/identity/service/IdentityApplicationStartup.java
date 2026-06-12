package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import vg.identity.entity.IdentityAccountEntity;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityUserSystemRole;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class IdentityApplicationStartup {

    private final UserDetailsManager userDetailsManager;
    private final IdentityUserServiceImpl userService;
    private final IdentityUserAuthorityService authorityService;
    private final IdentityAccountService accountService;

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationReady() {
        log.info("Identity application started");
        userDetailsManager.createUser(
                userService.getGuest()
        );
        createUser("vg", "vg", IdentityUserSystemRole.IDENTITY_ADMIN);
        createUser("g", "g", null);
        createUser("a", "a", null);

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new PreAuthenticatedAuthenticationToken(
                                "vg",
                                "vg",
                                userDetailsManager.loadUserByUsername("vg").getAuthorities()
                        )
                );

        List.of("Acc1", "Acc2", "Acc3", "Acc4", "Acc5").forEach(accName ->
                accountService.create(
                        IdentityAccountEntity.builder().name(accName).build()
                )
        );

        SecurityContextHolder.clearContext();
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
