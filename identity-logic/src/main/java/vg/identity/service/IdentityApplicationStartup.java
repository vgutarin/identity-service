package vg.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Component;
import vg.identity.model.IdentityPermission;
import vg.identity.model.principal.IdentityInternalUser;
import vg.identity.model.IdentityInternalUserSystemRole;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.access.Permission;

import java.util.List;

import static vg.identity.model.IdentityInternalUserSystemRole.OWNER;

@Slf4j
@RequiredArgsConstructor
@Component
@Profile("dev")
public class IdentityApplicationStartup {

    private final IdentityUserService userService;
    private final IdentityUserAuthorityService authorityService;
    private final IdentityWorkspaceService workspaceService;
    private final IdentityPermissionService permissionService;
    private final IdentityRoleTemplateService roleTemplateService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Identity application started");
        try {
            setCurrentUserAsOwner();
            var ownerEmail = "vgutarin@gmail.com";
            if (null != userService.findByUsername(ownerEmail)) {
                log.info("User with name {} already exists", userService.findByUsername(ownerEmail).getUsername());
                return;
            }

            createUser(ownerEmail, "vg", IdentityInternalUserSystemRole.OWNER);
//            createUser("app.vgutarin@gmail.com", "g", null);
//            createUser("b", "b", null);

            for (var permission : Permission.ALL) {
                permissionService.create(IdentityPermission.builder().name(permission).build());
            }

//            var i = 0;
//            for (var roleName : List.of("Role1", "Role2", "Role3")) {
//                var template = roleTemplateService.create(
//                        IdentityRoleTemplate.builder()
//                                .name(roleName)
//                                .description(roleName + " description " + i)
//                                .build()
//                );
//                roleTemplateService.addPermission(template.getId(), Permission.ALL[i++]);
//                roleTemplateService.addPermission(template.getId(), Permission.ALL[i++]);
//            }

            workspaceService.create(
                    IdentityWorkspace.builder().name("System").build()
            );

//            List.of("Workspace1", "Workspace2").forEach(workspaceName ->
//                    workspaceService.create(
//                            IdentityWorkspace.builder().name(workspaceName).build()
//                    )
//            );
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public static void setCurrentUserAsOwner() {
        var authorities = List.of(
                new SimpleGrantedAuthority(
                        IdentityUserAuthorityService.normalizeRoleName(OWNER.name())
                )
        );
        var authentication = new PreAuthenticatedAuthenticationToken(
                IdentityInternalUser.builder()
                        .username("owner")
                        .password("psw")
                        .authorities(authorities)
                        .build(),
                "psw",
                authorities
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void createUser(String username, String psw, IdentityInternalUserSystemRole role) {

        var user = userService.create(
                IdentityInternalUser.builder()
                        .username(username)
                        .password(psw)
                        .build()
        );

        if (null != role) {
            authorityService.assignAuthorityTmpInsecure(user, role);
        }

        log.info("Created user: {}", username);
    }
}
