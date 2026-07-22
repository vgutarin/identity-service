package vg.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import vg.identity.service.IdentityActionLinkBuilder;
import vg.identity.service.IdentityActionLinkBuilderDefault;

import java.time.Clock;
import java.util.Map;

@Configuration
@ComponentScan
@EnableJpaRepositories
@EntityScan
@EnableConfigurationProperties({
        EncryptionProperties.class,
        EmailProperties.class,
        IdentityActionTokenProperties.class
})
@EnableMethodSecurity
@EnableScheduling
public class IdentityLogicConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        var defaultId = "argon2";
        return new DelegatingPasswordEncoder(
                defaultId,
                Map.of(
                        defaultId, Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()
                )
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * Host-relative link builder used when no frontend supplies its own. The Vaadin module contributes an
     * {@link IdentityActionLinkBuilder} bean that produces absolute URLs, which suppresses this fallback.
     */
    @Bean
    @ConditionalOnMissingBean
    public IdentityActionLinkBuilder actionLinkBuilder(IdentityActionTokenProperties properties) {
        return new IdentityActionLinkBuilderDefault(properties);
    }
}
