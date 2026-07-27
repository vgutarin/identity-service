package vg.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import vg.identity.security.ApiKeyAuthenticationFilter;

@Configuration
public class RestSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
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
}
