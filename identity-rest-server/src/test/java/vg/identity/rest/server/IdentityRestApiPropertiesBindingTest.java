package vg.identity.rest.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the toggle configuration binds unconditionally and fails fast on an invalid value (US3 /
 * FR-009 / SC-006). Because {@link IdentityRestApiProperties} is bound via {@code @EnableConfigurationProperties}
 * in the always-on {@link IdentityRestApiAutoConfig}, an unparseable value fails startup even when the API
 * would be disabled.
 */
class IdentityRestApiPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IdentityRestApiAutoConfig.class));

    @Test
    void startupFails_whenEnabledValueIsNotBoolean() {
        runner.withPropertyValues("identity.rest.api.enabled=maybe")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bindsDisabled_whenPropertyAbsent() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(IdentityRestApiProperties.class).isEnabled()).isFalse();
        });
    }

    @Test
    void bindsEnabled_whenPropertyTrue() {
        runner.withPropertyValues("identity.rest.api.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IdentityRestApiProperties.class).isEnabled()).isTrue();
                });
    }
}
