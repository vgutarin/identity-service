package vg.identity.rest.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import vg.identity.rest.server.security.ApiKeyAuthenticationFilter;

/**
 * Security for the API-key-authenticated REST surface.
 *
 * <p>Registered only when {@code identity.rest.api.enabled=true}. The {@code /api/**} chain runs at
 * {@link Order @Order(1)} so that, when this module is co-hosted with a UI (e.g. the Vaadin app),
 * API paths are always matched here and never by the UI's security chain.</p>
 */
@Configuration
@ConditionalOnBooleanProperty("identity.rest.api.enabled")
public class RestSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) {
        http
                .securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .addFilterBefore(apiKeyAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint((request, response, exception) ->
                        response.setStatus(401)
                ));
        return http.build();
    }

    /**
     * Prevents Spring Boot from auto-registering the API-key filter as a global servlet filter (which would
     * intercept every request, including the co-hosted UI). The filter runs only inside the {@code /api/**}
     * security chain via {@code addFilterBefore} above.
     */
    @Bean
    FilterRegistrationBean<ApiKeyAuthenticationFilter> apiKeyAuthenticationFilterRegistration(ApiKeyAuthenticationFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
