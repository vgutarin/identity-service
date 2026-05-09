package vg.identity;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
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
    public User anonymous(UserServiceImpl userService) {
        return createUser(userService, "anonymous", "{noop}anonymous");
    }

    //TODO implement UserDetailsManager
    @Bean
    public UserDetailsManager userDetailsService(UserServiceImpl userService, User anonymous) {
        return new InMemoryUserDetailsManager(
                anonymous,
                createUser(userService, "g","{noop}g"),
                createUser(userService, "a","{noop}a")
        );
    }

    //TODO rework storage
    private User createUser(UserService userService, String username, String psw) {
        return userService.create(
                User.builder()
                    .username(username)
                    .password(psw)
                    .build()
        );
    }
}
