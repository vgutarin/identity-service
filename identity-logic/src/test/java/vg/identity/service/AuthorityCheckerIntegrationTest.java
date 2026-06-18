package vg.identity.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityApplicationEntity;
import vg.identity.entity.IdentityPermissionEntity;
import vg.identity.entity.IdentityPrincipalEntity;
import vg.identity.entity.IdentityRoleAssignmentEntity;
import vg.identity.entity.IdentityRoleEntity;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.IdentityUser;
import vg.identity.repository.IdentityApplicationRepository;
import vg.identity.repository.IdentityPermissionRepository;
import vg.identity.repository.IdentityPrincipalRepository;
import vg.identity.repository.IdentityRoleAssignmentRepository;
import vg.identity.repository.IdentityRoleRepository;
import vg.identity.repository.IdentityRoleTemplateRepository;
import vg.identity.repository.IdentityUserChannelRepository;
import vg.identity.repository.IdentityUserRepository;
import vg.identity.repository.IdentityUserSystemRoleRepository;
import vg.identity.repository.IdentityWorkspaceRepository;
import vg.unique.id.service.UniqueIdService;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;

class AuthorityCheckerIntegrationTest extends BaseIntegrationTest {
    private static final String USERNAME = "authority-checker-user";

    @Autowired
    AuthorityChecker authorityChecker;
    @Autowired
    IdentityPrincipalService principalService;
    @Autowired
    UniqueIdService uniqueIdService;
    @Autowired
    EncryptionService encryptionService;
    @Autowired
    PlatformTransactionManager transactionManager;
    @PersistenceContext
    EntityManager entityManager;
    @Autowired
    IdentityRoleAssignmentRepository roleAssignmentRepository;
    @Autowired
    IdentityRoleRepository roleRepository;
    @Autowired
    IdentityRoleTemplateRepository roleTemplateRepository;
    @Autowired
    IdentityPermissionRepository permissionRepository;
    @Autowired
    IdentityApplicationRepository applicationRepository;
    @Autowired
    IdentityWorkspaceRepository workspaceRepository;
    @Autowired
    IdentityUserSystemRoleRepository systemRoleRepository;
    @Autowired
    IdentityUserChannelRepository channelRepository;
    @Autowired
    IdentityUserRepository userRepository;
    @Autowired
    IdentityPrincipalRepository principalRepository;

    @AfterEach
    void cleanUp() {
        roleAssignmentRepository.deleteAll();
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

    @Test
    @WithMockUser(username = USERNAME, roles = "OWNER")
    void hasAuthority_whenUserIsOwner_returnsTrue() {
        assertThat(authorityChecker.hasAuthority("anything")).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "OWNER")
    void hasAuthority_withScopeAndUserIsOwner_returnsTrue() {
        assertThat(authorityChecker.hasAuthority(Long.MAX_VALUE, "anything")).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withWorkspaceScopeAndUserHasAssignedRolePermissionOnWorkspace_returnsTrue() {
        var user = createCurrentUser();
        var workspace = createWorkspace();
        var permissionName = permissionName();
        var role = createRole(workspace, permissionName);
        assignRole(user, workspace.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(workspace.getUniqueId(), permissionName)).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withApplicationScopeAndUserHasAssignedRolePermissionOnWorkspace_returnsTrue() {
        var user = createCurrentUser();
        var workspace = createWorkspace();
        var application = createApplication(workspace);
        var permissionName = permissionName();
        var role = createRole(workspace, permissionName);
        assignRole(user, workspace.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(application.getUniqueId(), permissionName)).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withApplicationScopeAndUserHasAssignedRolePermissionOnApplication_returnsTrue() {
        var user = createCurrentUser();
        var workspace = createWorkspace();
        var application = createApplication(workspace);
        var permissionName = permissionName();
        var role = createRole(workspace, permissionName);
        assignRole(user, application.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(application.getUniqueId(), permissionName)).isTrue();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withWorkspacePermissionAssignedToOneWorkspace_returnsTrueOnlyForAssignedWorkspaceScope() {
        var user = createCurrentUser();
        var allowedWorkspace = createWorkspace();
        var allowedApplication = createApplication(allowedWorkspace);
        var forbiddenWorkspace = createWorkspace();
        var forbiddenApplication = createApplication(forbiddenWorkspace);
        var permissionName = permissionName();
        var role = createRole(allowedWorkspace, permissionName);
        assignRole(user, allowedWorkspace.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(allowedWorkspace.getUniqueId(), permissionName)).isTrue();
        assertThat(authorityChecker.hasAuthority(allowedApplication.getUniqueId(), permissionName)).isTrue();
        assertThat(authorityChecker.hasAuthority(forbiddenWorkspace.getUniqueId(), permissionName)).isFalse();
        assertThat(authorityChecker.hasAuthority(forbiddenApplication.getUniqueId(), permissionName)).isFalse();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withApplicationPermissionAssignedToOneApplication_returnsTrueOnlyForAssignedApplicationScope() {
        var user = createCurrentUser();
        var workspace = createWorkspace();
        var allowedApplication = createApplication(workspace);
        var forbiddenApplication = createApplication(workspace);
        var permissionName = permissionName();
        var role = createRole(workspace, permissionName);
        assignRole(user, allowedApplication.getUniqueId(), role);

        assertThat(authorityChecker.hasAuthority(allowedApplication.getUniqueId(), permissionName)).isTrue();
        assertThat(authorityChecker.hasAuthority(forbiddenApplication.getUniqueId(), permissionName)).isFalse();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withScopeAndPermissionIsNotAssigned_returnsFalse() {
        createCurrentUser();
        var workspace = createWorkspace();

        assertThat(authorityChecker.hasAuthority(workspace.getUniqueId(), permissionName())).isFalse();
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void hasAuthority_withScopeAndResourceIsUnknown_returnsFalse() {
        createCurrentUser();

        assertThat(authorityChecker.hasAuthority(Long.MAX_VALUE, permissionName())).isFalse();
    }

    private IdentityUser createCurrentUser() {
        var existing = principalService.findByUsername(USERNAME);
        if (existing != null) {
            return existing;
        }

        return principalService.create(IdentityUser.builder()
                .username(USERNAME)
                .password(nextString())
                .build());
    }

    private IdentityWorkspaceEntity createWorkspace() {
        return workspaceRepository.saveWithNewUniqueId(
                IdentityWorkspaceEntity.builder()
                        .name(nextString())
                        .build(),
                uniqueIdService
        );
    }

    private IdentityApplicationEntity createApplication(IdentityWorkspaceEntity workspace) {
        var name = nextString();
        var principal = principalRepository.saveWithNewUniqueId(
                IdentityPrincipalEntity.builder()
                        .displayName(name)
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
                    .name(name)
                    .nameHash(encryptionService.canonicalizeAndHash(name))
                    .data(nextString())
                    .build();

            entityManager.persist(entity);
            entityManager.flush();
            return entity;
        });
    }

    private IdentityRoleEntity createRole(IdentityWorkspaceEntity workspace, String permissionName) {
        var permission = permissionRepository.save(IdentityPermissionEntity.builder()
                .name(permissionName)
                .build());
        return roleRepository.save(IdentityRoleEntity.builder()
                .name(nextString())
                .workspace(workspace)
                .permissions(Set.of(permission))
                .build());
    }

    private void assignRole(IdentityUser user, long resourceUniqueId, IdentityRoleEntity role) {
        var principal = principalRepository.findById(user.getUniqueId()).orElseThrow();
        roleAssignmentRepository.save(IdentityRoleAssignmentEntity.builder()
                .principal(principal)
                .resourceUniqueId(resourceUniqueId)
                .role(role)
                .build());
    }

    private static String permissionName() {
        return "permission." + nextString().toLowerCase(Locale.ROOT);
    }
}
