package vg.identity.rest;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import vg.lib.rest.RestClientFactory;
import vg.identity.rest.v1.UserServiceApiRestClient;

@ComponentScan
@Configuration
public class IdentityRestClient {

    //TODO autowire
    private final RestClientFactory restClientFactory = new RestClientFactory();

    @Bean
    UserServiceApiRestClient userServiceApiRestClient(@Value("${vg.identity.rest.service.base-url:http://localhost:8080}") String serviceBaseUrl) {
        return restClientFactory.createClient(serviceBaseUrl, UserServiceApiRestClient.class);
    }
}
