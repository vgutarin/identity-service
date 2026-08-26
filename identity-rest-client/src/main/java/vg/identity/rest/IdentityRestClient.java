package vg.identity.rest;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import vg.identity.rest.v1.IdentityApplicationApiRestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityRestClientProperties.class)
public class IdentityRestClient {

    @Bean
    IdentityApplicationApiRestClient identityApplicationApiRestClient(IdentityRestClientProperties properties) {
        // JDK HttpClient (java.net.http) — pooled, keep-alive, HTTP/2 capable. Connect timeout is set on
        // the HttpClient; read timeout is applied per request by the factory.
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(requiredTimeout("connect-timeout", properties.getConnectTimeout()))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(requiredTimeout("read-timeout", properties.getReadTimeout()));
        var restClient = RestClient.builder()
                .baseUrl(validBaseUrl(properties.getBaseUrl()).toString())
                .defaultHeader(IdentityApplicationApiRestClient.API_KEY_HEADER, requiredApiKey(properties.getApiKey()))
                .requestFactory(requestFactory)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(IdentityApplicationApiRestClient.class);
    }

    private static URI validBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Property '" + IdentityRestClientProperties.CONFIG_PREFIX + ".base-url' must not be blank");
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
                "Property '" + IdentityRestClientProperties.CONFIG_PREFIX
                        + ".base-url' must be an absolute HTTP(S) URL without credentials, query, or fragment"
        );
    }

    private static Duration requiredTimeout(String name, Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(
                    "Property '" + IdentityRestClientProperties.CONFIG_PREFIX + "." + name
                            + "' must be a positive duration");
        }
        return value;
    }

    private static String requiredApiKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Property '" + IdentityRestClientProperties.CONFIG_PREFIX + ".api-key' must not be blank");
        }
        return value;
    }
}
