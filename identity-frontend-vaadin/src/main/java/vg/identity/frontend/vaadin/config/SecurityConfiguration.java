package vg.identity.frontend.vaadin.config;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import vg.identity.frontend.vaadin.auth.LoginView;

@EnableWebSecurity
@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        /**
         * Delegating the responsibility of general configuration
         * of HTTP security to the VaadinSecurityConfigurer.
         *
         * It's configuring the following:
         * - Vaadin's CSRF protection by ignoring internal framework requests,
         * - default request cache,
         * - ignoring public views annotated with @AnonymousAllowed,
         * - restricting access to other views/endpoints, and
         * - enabling ViewAccessChecker authorization.
         */

        // You can add any possible extra configurations of your own
        // here - the following is just an example:
        http.rememberMe(customizer -> customizer.alwaysRemember(false));

        // Configure your static resources with public access before calling
        // VaadinSecurityConfigurer.vaadin() as it adds final anyRequest matcher
        http.authorizeHttpRequests(auth -> {
            auth.requestMatchers("/admin-only/**").hasAnyRole("admin")
                .requestMatchers(
                        "/verify/**",
                        "/public/**",
                        "/h2-console",
                        "/h2-console/**"
                ).permitAll();
        });

        http.csrf(csrf -> csrf
                .ignoringRequestMatchers(PathRequest.toH2Console())
        );

        http.headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
        );

        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> {
            // This is important to register your login view to the
            // view access checker mechanism:
            configurer.loginView(LoginView.class);
        });

        return http.build();
    }


//    @Override
//    protected void configure(HttpSecurity http) throws Exception {
//        // Delegating the responsibility of general configurations
//        // of http security to the super class. It's configuring
//        // the followings: Vaadin's CSRF protection by ignoring
//        // framework's internal requests, default request cache,
//        // ignoring public views annotated with @AnonymousAllowed,
//        // restricting access to other views/endpoints, and enabling
//        // NavigationAccessControl authorization.
//        // You can add any possible extra configurations of your own
//        // here (the following is just an example):
//
//        // http.rememberMe().alwaysRemember(false);
//
//        // Configure your static resources with public access before calling
//        // super.configure(HttpSecurity) as it adds final anyRequest matcher
//        http
//            .authorizeHttpRequests(auth ->
//                auth
//                    .requestMatchers(new AntPathRequestMatcher("/public/**"))
//                    .permitAll()
//            );
//
//        super.configure(http);
//
//        // This is important to register your login view to the
//        // navigation access control mechanism:
//        setLoginView(http, LoginView.class);
//    }

    @Bean
    public AuthenticationEventPublisher authenticationEventPublisher
        (ApplicationEventPublisher applicationEventPublisher) {
        return new DefaultAuthenticationEventPublisher(applicationEventPublisher);
    }

//    @Override
//    public void configure(WebSecurity web) throws Exception {
//        // Customize your WebSecurity configuration.
//        super.configure(web);
//    }

}
