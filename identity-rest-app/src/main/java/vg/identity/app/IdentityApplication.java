package vg.identity.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Standalone runner for the identity REST API.
 *
 * <p>Thin entry point: the REST web beans live in {@code identity-rest-server} and the domain beans
 * in {@code identity-logic}, both wired via auto-configuration. This module sets
 * {@code identity.rest.api.enabled=true} so the REST surface is always on for the standalone
 * deployment.</p>
 */
@EnableJpaAuditing
@SpringBootApplication
public class IdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }

}
