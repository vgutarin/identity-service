package vg.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityRoleAssignmentEntity;
import vg.identity.entity.IdentityRoleEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.mapper.IdentityUserMapper;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityApplicationUserClaimRepository;
import vg.identity.repository.IdentityApplicationUserRepository;
import vg.identity.repository.IdentityApiKeyRepository;
import vg.identity.repository.IdentityCommandRepository;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityRoleAssignmentRepository;
import vg.identity.repository.IdentityRoleRepository;
import vg.identity.repository.IdentityRoleTemplateRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityActionTokenRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityUserResourcePermissionRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.identity.repository.IdentityWorkspaceScopeClaimDictionaryRepository;
import vg.identity.service.EncryptionService;
import vg.test.containers.starters.Mysql8ContainerStarter;
import vg.unique.id.service.UniqueIdService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

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
    protected IdentityApplicationUserClaimRepository applicationUserClaimRepository;
    @Autowired
    protected IdentityApplicationUserRepository applicationUserRepository;
    @Autowired
    protected IdentityApiKeyRepository apiKeyRepository;
    @Autowired
    protected IdentityCommandRepository commandRepository;
    @Autowired
    protected IdentityUserSystemRoleRepository systemRoleRepository;
    @Autowired
    protected IdentityUserChannelRepository channelRepository;
    @Autowired
    protected IdentityActionTokenRepository actionTokenRepository;
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
    protected IdentityWorkspaceScopeClaimDictionaryRepository scopeClaimDictionaryRepository;
    @Autowired
    protected IdentityPrincipalRepository principalRepository;
    @Autowired
    private UniqueIdService uniqueIdService;
    @Autowired
    private IdentityUserMapper identityUserMapper;
    @Autowired
    private EncryptionService encryptionService;
    @Autowired
    protected PlatformTransactionManager transactionManager;
    @PersistenceContext
    protected EntityManager entityManager;


    @AfterEach
    protected void cleanUp() {
        commandRepository.deleteAll();
        applicationUserClaimRepository.deleteAll();
        applicationUserRepository.deleteAll();
        scopeClaimDictionaryRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        resourcePermissionRepository.deleteAll();
        actionTokenRepository.deleteAll();
        roleRepository.deleteAll();
        roleTemplateRepository.deleteAll();
        apiKeyRepository.deleteAll();
        applicationRepository.deleteAll();
        workspaceRepository.deleteAll();
        permissionRepository.deleteAll();
        systemRoleRepository.deleteAll();
        channelRepository.deleteAll();
        userRepository.deleteAll();
        principalRepository.deleteAll();
    }

    protected IdentityUser createIdentityUser(String username) {
        var existing = userRepository.findByPrincipal_NameHash(encryptionService.hashPrincipalName(username));
        if (existing.isPresent()) {
            return identityUserMapper.toModel(existing.get());
        }

        var user = IdentityUser.builder()
                .username(username)
                .password(nextString())
                .build();

        var principal = createPrincipal(user);
        var userEntity = identityUserMapper.toEntity(user);
        userEntity.setUniqueId(principal.getUniqueId());
        var saved = userRepository.save(userEntity);
        saved.setPrincipal(principal);
        return identityUserMapper.toModel(saved);
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


    private IdentityPrincipalEntity createPrincipal(IdentityUser user) {
        var principal = IdentityPrincipalEntity.builder()
                .name(user.getUsername())
                .nameHash(encryptionService.hashPrincipalName(user.getUsername()))
                .displayName(user.getUsername())
                .status(IdentityPrincipalStatus.ACTIVE)
                .type(IdentityPrincipalType.USER)
                .build();
        return principalRepository.saveWithNewUniqueId(principal, uniqueIdService);
    }

    protected IdentityWorkspaceEntity createWorkspace() {
        var workspace = workspaceRepository.saveWithNewUniqueId(
                IdentityWorkspaceEntity.builder().name(nextString()).build(),
                uniqueIdService
        );
        workspaceRepository.flush();
        return workspace;
    }

    protected IdentityApplicationEntity createApplication(IdentityWorkspaceEntity workspace) {
        return createApplication(workspace, nextString());
    }

    protected IdentityApplicationEntity createApplication(IdentityWorkspaceEntity workspace, String payload) {
        var uri = nextString();
        var principal = principalRepository.saveWithNewUniqueId(
                IdentityPrincipalEntity.builder()
                        .displayName(nextString())
                        .name(uri)
                        .nameHash(encryptionService.hashPrincipalName(uri))
                        .status(IdentityPrincipalStatus.ACTIVE)
                        .type(IdentityPrincipalType.APPLICATION)
                        .build(),
                uniqueIdService
        );
        return new TransactionTemplate(transactionManager).execute(status -> {
            var entity = IdentityApplicationEntity.builder()
                    .uniqueId(principal.getUniqueId())
                    .principal(entityManager.getReference(IdentityPrincipalEntity.class, principal.getUniqueId()))
                    .workspace(entityManager.getReference(IdentityWorkspaceEntity.class, workspace.getUniqueId()))
                    .payload(payload)
                    .build();
            entityManager.persist(entity);
            entityManager.flush();
            return entity;
        });
    }

    protected IdentityRoleEntity createRole(IdentityWorkspaceEntity workspace, String permissionName) {
        var permission = permissionRepository.findByName(permissionName)
                .orElseGet(() -> permissionRepository.save(IdentityPermissionEntity.builder()
                        .name(permissionName)
                        .build()));
        return roleRepository.save(IdentityRoleEntity.builder()
                .name(nextString())
                .workspace(workspace)
                .permissions(Set.of(permission))
                .build());
    }

    protected void assignRole(IdentityUser user, long resourceUniqueId, IdentityRoleEntity role) {
        var principal = principalRepository.findById(user.getUniqueId()).orElseThrow();
        roleAssignmentRepository.save(IdentityRoleAssignmentEntity.builder()
                .principal(principal)
                .resourceUniqueId(resourceUniqueId)
                .role(role)
                .build());
    }

    /** Convenience: create a role carrying {@code permissionName} in the workspace and assign it to the user there. */
    protected IdentityRoleEntity grantWorkspacePermission(IdentityUser user, IdentityWorkspaceEntity workspace, String permissionName) {
        var role = createRole(workspace, permissionName);
        assignRole(user, workspace.getUniqueId(), role);
        return role;
    }
}
