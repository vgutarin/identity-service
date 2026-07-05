package vg.identity;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.ActiveProfiles;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityCommandRepository;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityRoleAssignmentRepository;
import vg.identity.repository.IdentityRoleRepository;
import vg.identity.repository.IdentityRoleTemplateRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityUserResourcePermissionRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.identity.service.IdentityPrincipalService;
import vg.test.containers.starters.Mysql8ContainerStarter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static vg.test.TestHelper.nextString;

@SpringBootTest
@ActiveProfiles({"test", "integration"})
@EnableJpaAuditing
@SpringBootApplication
public class BaseIntegrationTest implements Mysql8ContainerStarter {

    @Autowired
    protected IdentityRoleRepository roleRepository;
    @Autowired
    protected IdentityApplicationRepository applicationRepository;
    @Autowired
    protected IdentityCommandRepository commandRepository;
    @Autowired
    protected IdentityUserSystemRoleRepository systemRoleRepository;
    @Autowired
    protected IdentityUserChannelRepository channelRepository;
    @Autowired
    protected IdentityUserRepository userRepository;
    @Autowired
    protected IdentityRoleTemplateRepository roleTemplateRepository;
    @Autowired
    protected IdentityPermissionRepository permissionRepository;
    @Autowired
    protected IdentityRoleAssignmentRepository roleAssignmentRepository;
    @Autowired
    protected IdentityUserResourcePermissionRepository resourcePermissionRepository;
    @Autowired
    protected IdentityWorkspaceRepository workspaceRepository;
    @Autowired
    protected IdentityPrincipalRepository principalRepository;
    @Autowired
    protected IdentityPrincipalService principalService;


    @AfterEach
    protected void cleanUp() {
        commandRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        resourcePermissionRepository.deleteAll();
        roleRepository.deleteAll();
        roleTemplateRepository.deleteAll();
        applicationRepository.deleteAll();
        workspaceRepository.deleteAll();
        permissionRepository.deleteAll();
        systemRoleRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
        principalRepository.deleteAll();
    }

    protected IdentityUser createIdentityUser(String username) {
        var existing = principalService.findByUsername(username);
        if (existing != null) {
            return existing;
        }

        return principalService.create(IdentityUser.builder()
                .username(username)
                .password(nextString())
                .build());
    }

    protected static Clock clock = Clock.fixed(
            Instant.now(), ZoneOffset.UTC
    );

    @Configuration
    static class Cfg {
        @Bean
        public Clock clock() {
            return clock;
        }
    }
}
