package vg.identity.rest.server;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Always-on auto-configuration for the optional REST API.
 *
 * <p>It binds {@link IdentityRestApiProperties} unconditionally so an invalid
 * {@code identity.rest.api.enabled} value fails startup (fail-fast), and it reports the effective
 * mode at startup. The REST web beans themselves (controller, API-key filter, {@code /api/**}
 * security chain) are gated individually with {@code @ConditionalOnBooleanProperty} and are NOT
 * imported here, to avoid double-registration with {@code identity-logic}'s component scan.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(IdentityRestApiProperties.class)
public class IdentityRestApiAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(IdentityRestApiAutoConfig.class);

    private final IdentityRestApiProperties properties;

    public IdentityRestApiAutoConfig(IdentityRestApiProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void reportEffectiveMode() {
        if (properties.isEnabled()) {
            log.info("REST API is ENABLED (identity.rest.api.enabled=true): /api/** endpoints are exposed and require a valid API key.");
        } else {
            log.info("REST API is DISABLED (identity.rest.api.enabled=false): no /api/** endpoints are exposed.");
        }
    }
}
