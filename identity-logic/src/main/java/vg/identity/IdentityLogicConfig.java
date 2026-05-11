package vg.identity;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import vg.identity.model.User;
import vg.identity.service.UserService;
import vg.identity.service.UserServiceImpl;

@Configuration
@ComponentScan
@EnableJpaRepositories
@EntityScan
public class IdentityLogicConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public User anonymous(UserServiceImpl userService, PasswordEncoder passwordEncoder) {
        return createUser(userService, "anonymous", "anonymous", passwordEncoder);
    }

    //TODO implement UserDetailsManager
    @Bean
    public UserDetailsManager userDetailsService(UserServiceImpl userService, User anonymous, PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                anonymous,
                createUser(userService, "g","g", passwordEncoder),
                createUser(userService, "a","a", passwordEncoder)
        );
    }

    //TODO rework storage
    private User createUser(UserService userService, String username, String psw, PasswordEncoder passwordEncoder) {
        return userService.create(
                User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(psw))
                    .build()
        );
    }
}
