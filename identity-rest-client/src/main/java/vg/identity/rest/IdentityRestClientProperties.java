package vg.identity.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the API-key-authenticated Identity REST client.
 *
 * <p>The API key is required and must be supplied by a secret source, for example the
 * {@code VG_IDENTITY_REST_CLIENT_API_KEY} environment variable. It must not be committed to an
 * application properties file.</p>
 */
@ConfigurationProperties(IdentityRestClientProperties.CONFIG_PREFIX)
public class IdentityRestClientProperties {
    /** Prefix under which all Identity REST client properties are bound. */
    public static final String CONFIG_PREFIX = "vg.identity.rest-client";

    private String baseUrl = "http://localhost:8080";
    private String apiKey;

    /**
     * Timeout for establishing a connection to the identity service. Accepts the Spring duration
     * format (e.g. {@code 5s}, {@code 500ms}, {@code PT2S}); defaults to 5 seconds.
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * Timeout for reading the response once a connection is established. Accepts the Spring duration
     * format (e.g. {@code 5s}, {@code 500ms}, {@code PT2S}); defaults to 5 seconds.
     */
    private Duration readTimeout = Duration.ofSeconds(5);

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
