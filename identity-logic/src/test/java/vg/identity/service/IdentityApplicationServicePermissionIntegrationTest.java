package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.access.Permission;
import vg.identity.model.application.TelegramBot;
import vg.unique.id.model.UniqueId;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

class IdentityApplicationServicePermissionIntegrationTest extends BaseIntegrationTest {
    private static final String USERNAME = "application-permission-user";

    @Autowired
    IdentityApplicationService service;

    @Test
    void publicMethods_areSecuredWithExpectedPreAuthorizeExpressions() {
        var expectedExpressions = Map.of(
                "createTelegramBotApplication(UniqueId, String, TelegramBot)", "@authorityChecker.hasAuthority(#workspaceUniqueId, '" + Permission.App.CREATE + "')",
                "delete(UniqueId)", "@authorityChecker.hasAuthority(#uniqueId, '" + Permission.App.DELETE + "')",
                "findById(UniqueId)", "@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')",
                "getAuthenticatedApplication(UniqueId)", "@authorityChecker.isAuthenticatedApplication(#applicationUniqueId)",
                "findByWorkspaceUniqueId(UniqueId)", "@authorityChecker.hasAuthority(#workspaceUniqueId, '" + Permission.App.READ + "')",
                "getById(UniqueId)", "@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')",
                "update(IdentityApplication)", "@authorityChecker.hasAuthority(#application.getUniqueId(), '" + Permission.App.UPDATE + "')",
                "updateTelegramBotApplication(UniqueId, int, String, TelegramBot)", "@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.UPDATE + "')"
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
        var role = createRole(workspace, Permission.App.READ);
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

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void createTelegramBotApplication_whenUserDoesNotHaveAppCreatePermission_throwsAccessDeniedException() {
        createIdentityUser(USERNAME);
        var workspace = createWorkspace();

        assertThatThrownBy(() -> service.createTelegramBotApplication(
                new UniqueId(workspace.getUniqueId()),
                nextString(),
                TelegramBot.builder()
                        .token(nextString())
                        .build()
        )).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = USERNAME, roles = "USER")
    void updateTelegramBotApplication_whenUserDoesNotHaveAppUpdatePermission_throwsAccessDeniedException() {
        createIdentityUser(USERNAME);
        var workspace = createWorkspace();
        var application = createApplication(workspace);

        assertThatThrownBy(() -> service.updateTelegramBotApplication(
                new UniqueId(application.getUniqueId()),
                application.getVersion(),
                nextString(),
                TelegramBot.builder()
                        .token(nextString())
                        .build()
        )).isInstanceOf(AccessDeniedException.class);
    }

    private String signature(Method method) {
        var parameterTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + parameterTypes + ")";
    }
}
