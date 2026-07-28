package vg.identity.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import vg.identity.model.IdentityApplicationUserPrincipal;
import vg.identity.model.IdentityPrincipalStatus;
import vg.identity.model.IdentityPrincipalType;
import vg.identity.model.IdentityUser;
import vg.identity.model.access.Permission;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static vg.test.TestHelper.nextString;

/**
 * Exercises the write path of {@link IdentityApplicationClaimService#grantClaim} end-to-end against the
 * database as a real workspace administrator — the path the read-focused {@code IdentityApplicationClaimIntegrationTest}
 * cannot drive because it authenticates as an application principal, which {@code grantClaim} rejects.
 * Verifies claim normalization and per-workspace dictionary interning/dedup across users.
 */
class IdentityApplicationClaimGrantIntegrationTest extends BaseIntegrationTest {
    private static final String ADMIN = "workspace-admin";

    @Autowired
    private IdentityApplicationClaimService claimService;
    @Autowired
    private UniqueIdService uniqueIdService;
    @Autowired
    private EncryptionService encryptionService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @WithMockUser(username = ADMIN, roles = "USER")
    void grantClaim_normalizesTheClaimBeforeInterningAndReferencingIt() {
        var admin = createIdentityUser(ADMIN);
        var workspace = createWorkspace();
        var application = createApplication(workspace);
        grantClaimCreateAuthority(admin, workspace);
        var subject = createIdentityUser("subject-" + nextString());

        claimService.grantClaim(
                new UniqueId(application.getUniqueId()),
                subject.getUniqueId(),
                IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE,
                "  Orders.READ  "
        );

        var grants = applicationUserClaimRepository.findAll();
        assertThat(grants).hasSize(1);
        // Stored via the dictionary as the trimmed + lower-cased value, not the raw input.
        assertThat(grants.get(0).getClaim().getName()).isEqualTo("orders.read");
        assertThat(grants.get(0).getScope().getName()).isEqualTo(IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE);
        assertThat(scopeClaimDictionaryRepository.findByWorkspace_UniqueIdAndNameBlindIndex(
                workspace.getUniqueId(), encryptionService.hashCaseSensitive("orders.read"))).isPresent();
    }

    @Test
    @WithMockUser(username = ADMIN, roles = "USER")
    void grantClaim_whenSameClaimIsGrantedToTwoUsers_internsOneDictionaryEntryPerWorkspace() {
        var admin = createIdentityUser(ADMIN);
        var workspace = createWorkspace();
        var application = createApplication(workspace);
        grantClaimCreateAuthority(admin, workspace);
        var applicationUniqueId = new UniqueId(application.getUniqueId());
        var user1 = createIdentityUser("subject-1-" + nextString());
        var user2 = createIdentityUser("subject-2-" + nextString());

        claimService.grantClaim(applicationUniqueId, user1.getUniqueId(), IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, "orders.read");
        claimService.grantClaim(applicationUniqueId, user2.getUniqueId(), IdentityApplicationUserPrincipal.PERMISSIONS_SCOPE, "orders.read");

        var grants = applicationUserClaimRepository.findAll();
        assertThat(grants).hasSize(2);
        // Both grants reference the SAME interned scope entry and the SAME interned claim entry.
        assertThat(grants.stream().map(grant -> grant.getScope().getId()).distinct()).hasSize(1);
        assertThat(grants.stream().map(grant -> grant.getClaim().getId()).distinct()).hasSize(1);
        // The workspace dictionary holds exactly the two distinct strings (scope + claim), interned once each.
        assertThat(scopeClaimDictionaryRepository.findAll()).hasSize(2);
    }

    private IdentityWorkspaceEntity createWorkspace() {
        var saved = workspaceRepository.saveWithNewUniqueId(
                IdentityWorkspaceEntity.builder().name(nextString()).build(),
                uniqueIdService
        );
        workspaceRepository.flush();
        return saved;
    }

    private IdentityApplicationEntity createApplication(IdentityWorkspaceEntity workspace) {
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
                    .payload(nextString())
                    .build();
            entityManager.persist(entity);
            entityManager.flush();
            return entity;
        });
    }

    private void grantClaimCreateAuthority(IdentityUser admin, IdentityWorkspaceEntity workspace) {
        var permission = permissionRepository.findByName(Permission.App.CLAIM_CREATE)
                .orElseGet(() -> permissionRepository.save(IdentityPermissionEntity.builder()
                        .name(Permission.App.CLAIM_CREATE)
                        .build()));
        var role = roleRepository.save(IdentityRoleEntity.builder()
                .name(nextString())
                .workspace(workspace)
                .permissions(Set.of(permission))
                .build());
        var principal = principalRepository.findById(admin.getUniqueId()).orElseThrow();
        roleAssignmentRepository.save(IdentityRoleAssignmentEntity.builder()
                .principal(principal)
                .resourceUniqueId(workspace.getUniqueId())
                .role(role)
                .build());
    }
}
