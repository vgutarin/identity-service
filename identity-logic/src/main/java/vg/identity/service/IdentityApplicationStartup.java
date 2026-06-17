package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import vg.identity.model.IdentityUser;
import vg.identity.model.IdentityUserSystemRole;
import vg.identity.model.IdentityWorkspace;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class IdentityApplicationStartup {

    private final UserDetailsManager userDetailsManager;
    private final IdentityPrincipalService principalService;
    private final IdentityUserAuthorityService authorityService;
    private final IdentityWorkspaceService workspaceService;

    @EventListener(ApplicationStartedEvent.class)
    public void onApplicationReady() {
        log.info("Identity application started");
        userDetailsManager.createUser(
                principalService.getGuest()
        );
        createUser("vg", "vg", IdentityUserSystemRole.OWNER);
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

        List.of("Workspace1", "Workspace2", "Workspace3", "Workspace4", "Workspace5").forEach(workspaceName ->
                workspaceService.create(
                        IdentityWorkspace.builder().name(workspaceName).build()
                )
        );

        SecurityContextHolder.clearContext();
    }

    private void createUser(String username, String psw, IdentityUserSystemRole role) {

        var user = principalService.create(
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
