package vg.identity.rest.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.SecurityFilterChain;
import vg.identity.rest.server.controller.IdentityApplicationController;
import vg.identity.rest.server.security.ApiKeyAuthenticationFilter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the safe-by-default toggle: when {@code identity.rest.api.enabled} is unset or {@code false},
 * the REST web beans do not register, so no {@code /api/**} surface exists (US1 / FR-002 / FR-005 / SC-002).
 *
 * <p>{@link ScanConfig} mirrors how {@code identity-logic}'s {@code @ComponentScan("vg.identity")} discovers
 * this package at runtime — the class-level {@code @ConditionalOnBooleanProperty} is what keeps the beans
 * out of the context regardless of who scans them. Enabled-mode behavior is covered by the full-context
 * test in US2.</p>
 */
class RestApiToggleTest {

    @Configuration
    @ComponentScan(basePackages = {
            "vg.identity.rest.server.controller",
            "vg.identity.rest.server.security",
            "vg.identity.rest.server.config"
    })
    static class ScanConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityRestApiAutoConfig.class))
            .withUserConfiguration(ScanConfig.class);

    @Test
    void restApiBeansAbsentAndModeDisabled_whenPropertyUnset() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdentityApplicationController.class);
            assertThat(context).doesNotHaveBean(ApiKeyAuthenticationFilter.class);
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
            assertThat(context.getBean(IdentityRestApiProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void restApiBeansAbsent_whenPropertyExplicitlyFalse() {
        runner.withPropertyValues("identity.rest.api.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(IdentityApplicationController.class);
            assertThat(context).doesNotHaveBean(ApiKeyAuthenticationFilter.class);
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
        });
    }
}
