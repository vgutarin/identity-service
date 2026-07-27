package vg.identity.rest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;

import java.net.URI;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityRestClientProperties.class)
public class IdentityRestClient {

    @Bean
    IdentityApplicationApiRestClient identityApplicationApiRestClient(IdentityRestClientProperties properties) {
        var restClient = RestClient.builder()
                .baseUrl(validBaseUrl(properties.getBaseUrl()).toString())
                .defaultHeader(IdentityApplicationApiRestClient.API_KEY_HEADER, requiredApiKey(properties.getApiKey()))
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(IdentityApplicationApiRestClient.class);
    }

    private static URI validBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Property 'vg.identity.rest-client.base-url' must not be blank");
        }

        try {
            var uri = URI.create(value);
            var scheme = uri.getScheme();
            if (!uri.isAbsolute()
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                throw invalidBaseUrl();
            }
            return uri;
        } catch (IllegalArgumentException ignored) {
            throw invalidBaseUrl();
        }
    }

    private static IllegalStateException invalidBaseUrl() {
        return new IllegalStateException(
                "Property 'vg.identity.rest-client.base-url' must be an absolute HTTP(S) URL without credentials, query, or fragment"
        );
    }

    private static String requiredApiKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Property 'vg.identity.rest-client.api-key' must not be blank");
        }
        return value;
    }
}
