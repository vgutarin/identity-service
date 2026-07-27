package vg.identity.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import vg.identity.BaseIntegrationTest;
import vg.identity.model.access.Permission;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityApiKeyServicePermissionIntegrationTest extends BaseIntegrationTest {

    @Test
    void publicMethods_haveExpectedSecurityConfiguration() {
        var expectedExpressions = Map.of(
                "issueForApplication(UniqueId, String, Instant)", "@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.UPDATE + "')",
                "findForApplication(UniqueId)", "@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.READ + "')",
                "revokeForApplication(UniqueId, UUID)", "@authorityChecker.hasAuthority(#applicationUniqueId, '" + Permission.App.UPDATE + "')"
        );
        var intentionallyUnsecuredMethods = Set.of("authenticate(String)");
        var expectedPublicMethods = new HashSet<>(expectedExpressions.keySet());
        expectedPublicMethods.addAll(intentionallyUnsecuredMethods);

        var publicMethods = Arrays.stream(IdentityApiKeyService.class.getMethods())
                .filter(method -> method.getDeclaringClass().equals(IdentityApiKeyService.class))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .collect(Collectors.toMap(this::signature, method -> method));

        assertThat(publicMethods.keySet()).containsExactlyInAnyOrderElementsOf(expectedPublicMethods);

        expectedExpressions.forEach((signature, expectedExpression) -> {
            var preAuthorize = publicMethods.get(signature).getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).as(signature).isNotNull();
            assertThat(preAuthorize.value()).as(signature).isEqualTo(expectedExpression);
        });
        intentionallyUnsecuredMethods.forEach(signature ->
                assertThat(publicMethods.get(signature).getAnnotation(PreAuthorize.class)).as(signature).isNull()
        );
    }

    private String signature(Method method) {
        var parameterTypes = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + parameterTypes + ")";
    }
}
