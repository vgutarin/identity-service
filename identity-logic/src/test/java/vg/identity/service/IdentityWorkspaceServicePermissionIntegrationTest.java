package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.entity.IdentityWorkspaceEntity;
import vg.identity.model.IdentityRole;
import vg.identity.model.IdentityWorkspace;
import vg.identity.model.access.Permission;
import vg.unique.id.model.UniqueId;
import vg.unique.id.service.UniqueIdService;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "USER")
class IdentityWorkspaceServicePermissionIntegrationTest extends BaseIntegrationTest {
    private static final String USERNAME = "john";

    @Autowired
    IdentityWorkspaceService service;
    @Autowired
    UniqueIdService uniqueIdService;

    @BeforeEach
    void setUp() {
        createIdentityUser(USERNAME);
    }

    @Test
    void publicMethods_areSecuredWithExpectedPreAuthorizeExpressions() {
        var expectedExpressions = Map.of(
                "addUser(UniqueId, String)", "@authorityChecker.hasAuthority(#uniqueId, '" + Permission.User.CREATE + "')",
                "create(IdentityWorkspace)", "@authorityChecker.hasAuthority('" + Permission.Workspace.CREATE + "')",
                "createRole(UniqueId, IdentityRole)", "@authorityChecker.hasAuthority(#uniqueId, '" + Permission.Role.CREATE + "')",
                "delete(UniqueId)", "@authorityChecker.hasAuthority(#uniqueId, '" + Permission.Workspace.DELETE + "')",
                "getAll()", "@authorityChecker.hasAuthority('" + Permission.Workspace.READ + "')",
                "getById(UniqueId)", "@authorityChecker.hasAuthority(#uniqueId, '" + Permission.Workspace.READ + "')",
                "getUsers(UniqueId)", "@authorityChecker.hasAuthority(#uniqueId, '" + Permission.User.READ + "')",
                "update(IdentityWorkspace)", "@authorityChecker.hasAuthority(#workspace.getUniqueId(), '" + Permission.Workspace.UPDATE + "')"
        );

        var publicMethods = Arrays.stream(IdentityWorkspaceService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(IdentityWorkspaceService.class))
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
    void create_whenUserIsNotAdmin_throwsAccessDeniedException() {
        var count = workspaceRepository.count();

        assertThatThrownBy(() -> service.create(buildWorkspace()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(workspaceRepository.count()).isEqualTo(count);
    }

    @Test
    void getById_whenUserDoesNotHaveResourceAuthority_throwsAccessDeniedException() {
        var saved = saveWorkspace();

        assertThatThrownBy(() -> service.getById(new UniqueId(saved.getUniqueId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_whenUserIsNotAdmin_throwsAccessDeniedException() {
        var saved = saveWorkspace();
        var newName = nextString();

        assertThatThrownBy(() -> service.update(
                IdentityWorkspace.builder()
                        .uniqueId(new UniqueId(saved.getUniqueId()))
                        .version(saved.getVersion())
                        .name(newName)
                        .build()
        )).isInstanceOf(AccessDeniedException.class);

        assertThat(workspaceRepository.findById(saved.getUniqueId()))
                .hasValueSatisfying(workspace -> assertThat(workspace.getName()).isEqualTo(saved.getName()));
    }

    @Test
    void delete_whenUserIsNotAdmin_throwsAccessDeniedException() {
        var saved = saveWorkspace();

        assertThatThrownBy(() -> service.delete(new UniqueId(saved.getUniqueId())))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(workspaceRepository.findById(saved.getUniqueId())).isPresent();
    }

    @Test
    void createRole_whenUserDoesNotHaveRoleCreatePermission_throwsAccessDeniedException() {
        var saved = saveWorkspace();

        assertThatThrownBy(() -> service.createRole(
                new UniqueId(saved.getUniqueId()),
                IdentityRole.builder()
                        .name(nextString())
                        .build()
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void addUser_whenUserDoesNotHaveUserCreatePermission_throwsAccessDeniedException() {
        var saved = saveWorkspace();

        assertThatThrownBy(() -> service.addUser(new UniqueId(saved.getUniqueId()), "john@example.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getUsers_whenUserDoesNotHaveUserReadPermission_throwsAccessDeniedException() {
        var saved = saveWorkspace();

        assertThatThrownBy(() -> service.getUsers(new UniqueId(saved.getUniqueId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    private IdentityWorkspaceEntity saveWorkspace() {
        var saved = workspaceRepository.saveWithNewUniqueId(buildWorkspaceEntity(), uniqueIdService);
        workspaceRepository.flush();
        return saved;
    }

    private IdentityWorkspace buildWorkspace() {
        return IdentityWorkspace.builder()
                .name(nextString())
                .build();
    }

    private IdentityWorkspaceEntity buildWorkspaceEntity() {
        return IdentityWorkspaceEntity.builder()
                .name(nextString())
                .build();
    }

    private String signature(Method method) {
        var parameterTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + parameterTypes + ")";
    }
}
