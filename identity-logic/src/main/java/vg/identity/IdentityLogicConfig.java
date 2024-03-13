package vg.identity;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import vg.identity.model.User;
import vg.unique.id.service.UniqueIdService;

@Configuration
@ComponentScan
@EnableJpaRepositories
@EntityScan
public class IdentityLogicConfig {


    @Bean
    public User anonymous(UniqueIdService uniqueIdService) {
        return
                User.builder()
                        .uniqueId(uniqueIdService.getNext())
                        .username("anonymous")
                        .password("{noop}anonymous")
                        .build();
    }


    //TODO implement UserDetailsManager
    @Bean
    public UserDetailsManager userDetailsService(UniqueIdService uniqueIdService) {
        UserDetails admin =
                User.builder()
                        .uniqueId(uniqueIdService.getNext())
                        .username("golubov")
                        .password("{noop}golubov")
                        .build();
        UserDetails user =
                User.builder()
                        .uniqueId(uniqueIdService.getNext())
                        .username("alex")
                        .password("{noop}alex")
                        .build();
        return new InMemoryUserDetailsManager(admin, user);
    }
}
