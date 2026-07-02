package vg.identity.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
import vg.identity.model.access.Permission;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

class IdentityApplicationServicePermissionIntegrationTest extends BaseIntegrationTest {
    private static final String USERNAME = "application-permission-user";

    @Autowired
    IdentityApplicationService service;
    @Autowired
    UniqueIdService uniqueIdService;
    @Autowired
    EncryptionService encryptionService;
    @Autowired
    PlatformTransactionManager transactionManager;
    @PersistenceContext
    EntityManager entityManager;

    @Test
    void publicMethods_areSecuredWithExpectedPreAuthorizeExpressions() {
        var expectedExpressions = Map.of(
                "delete(UniqueId)", "@authorityChecker.hasAuthority(#uniqueId, '" + Permission.App.DELETE + "')",
                "findById(UniqueId)", "@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')",
                "findByWorkspaceUniqueId(UniqueId)", "@authorityChecker.hasAuthority(#workspaceUniqueId, '" + Permission.App.READ + "')",
                "getById(UniqueId)", "@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')",
                "update(IdentityApplication)", "@authorityChecker.hasAuthority(#application.getUniqueId(), '" + Permission.App.UPDATE + "')"
        );

        var publicMethods = Arrays.stream(IdentityApplicationService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(IdentityApplicationService.class))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .collect(Collectors.toMap(this::signature, method -> method));

        assertThat(publicMethods.keySet()).containsExactlyInAnyOrderElementsOf(expectedExpressions.keySet());
        expectedExpressions.forEach((signature, expectedExpression) -> {
            var preAuthorize = publicMethods.get(signature).getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).as(signature).isNotNull();
            assertThat(preAuthorize.value()).as(signature).isEqualTo(expectedExpression);
        });
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void findByWorkspaceUniqueId_whenUserHasAppReadPermission_returnsApplications() {
        var user = createIdentityUser(USERNAME);
        var workspace = createWorkspace();
        var application = createApplication(workspace);
        var role = createRole(workspace);
        assignRole(user, workspace.getUniqueId(), role);

        assertThat(service.findByWorkspaceUniqueId(new UniqueId(workspace.getUniqueId())))
                .extracting(identityApplication -> identityApplication.getUniqueId().getLongValue())
                .containsExactly(application.getUniqueId());
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void findByWorkspaceUniqueId_whenUserDoesNotHaveAppReadPermission_throwsAccessDeniedException() {
        createIdentityUser(USERNAME);
        var workspace = createWorkspace();
        createApplication(workspace);

        assertThatThrownBy(() -> service.findByWorkspaceUniqueId(new UniqueId(workspace.getUniqueId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    private IdentityWorkspaceEntity createWorkspace() {
        var saved = workspaceRepository.saveWithNewUniqueId(
                IdentityWorkspaceEntity.builder()
                        .name(nextString())
                        .build(),
                uniqueIdService
        );
        workspaceRepository.flush();
        return saved;
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

    private IdentityRoleEntity createRole(IdentityWorkspaceEntity workspace) {
        var permission = permissionRepository.findByName(Permission.App.READ)
                .orElseGet(() -> permissionRepository.save(IdentityPermissionEntity.builder()
                        .name(Permission.App.READ)
                        .build()));
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

    private String signature(Method method) {
        var parameterTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + parameterTypes + ")";
    }
}
