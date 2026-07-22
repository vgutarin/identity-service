package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.IdentityUser;
import vg.identity.model.access.Permission;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vg.test.TestHelper.nextString;

@WithMockUser(username = "john", roles = "USER")
class IdentityUserServicePermissionIntegrationTest extends BaseIntegrationTest {
    private static final String USERNAME = "john";

    @Autowired
    IdentityUserService service;

    @BeforeEach
    void setUp() {
        createIdentityUser(USERNAME);
    }

    @Test
    void publicMethods_areSecuredWithExpectedPreAuthorizeExpressions() {
        var expectedExpressions = Map.of(
                "create(IdentityUser)", "@authorityChecker.hasAuthority('" + Permission.User.CREATE + "')",
                "update(IdentityUser)", "@authorityChecker.hasAuthority('" + Permission.User.UPDATE + "')",
                "findByUsername(String)", "@authorityChecker.hasAuthority('" + Permission.User.READ + "')",
                "findAll()", "@authorityChecker.hasAuthority('" + Permission.User.READ + "')"
        );

        var publicMethods = Arrays.stream(IdentityUserService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(IdentityUserService.class))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                // loadUserByUsername is the Spring Security UserDetailsService entry point: it runs during
                // authentication, before any principal exists, so it is intentionally not @PreAuthorize-secured.
                .filter(method -> !method.getName().equals("loadUserByUsername"))
                .collect(Collectors.toMap(this::signature, method -> method));

        assertThat(publicMethods.keySet()).containsExactlyInAnyOrderElementsOf(expectedExpressions.keySet());
        expectedExpressions.forEach((signature, expectedExpression) -> {
            var preAuthorize = publicMethods.get(signature).getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).as(signature).isNotNull();
            assertThat(preAuthorize.value()).as(signature).isEqualTo(expectedExpression);
        });
    }

    @Test
    void create_whenUserDoesNotHavePermission_throwsAccessDeniedException() {
        var count = userRepository.count();

        assertThatThrownBy(() -> service.create(IdentityUser.builder()
                .username(nextString())
                .build()))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(userRepository.count()).isEqualTo(count);
    }

    @Test
    void update_whenUserDoesNotHavePermission_throwsAccessDeniedException() {
        var user = createIdentityUser(nextString());
        var originalUsername = user.getUsername();

        user.setUsername(nextString());

        assertThatThrownBy(() -> service.update(user))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(userRepository.findById(user.getUniqueId().getLongValue()))
                .hasValueSatisfying(u -> assertThat(u.getPrincipal().getName()).isEqualTo(originalUsername));
    }

    @Test
    void findByUsername_whenUserDoesNotHavePermission_throwsAccessDeniedException() {
        var otherUsername = nextString();
        createIdentityUser(otherUsername);

        assertThatThrownBy(() -> service.findByUsername(otherUsername))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void findAll_whenUserDoesNotHavePermission_throwsAccessDeniedException() {
        assertThatThrownBy(() -> service.findAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    private String signature(Method method) {
        var parameterTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + parameterTypes + ")";
    }
}
