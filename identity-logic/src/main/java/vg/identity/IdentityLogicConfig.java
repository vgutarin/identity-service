package vg.identity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

import java.util.Map;

@Configuration
@ComponentScan
@EnableJpaRepositories
@EntityScan
@EnableConfigurationProperties(EncryptionProperties.class)
@EnableMethodSecurity
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


    //TODO implement UserDetailsManager
    @Bean
    public UserDetailsManager userDetailsService() {
        return new InMemoryUserDetailsManager();
    }

}
