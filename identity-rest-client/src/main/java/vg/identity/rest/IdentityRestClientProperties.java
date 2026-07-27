package vg.identity.rest;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the API-key-authenticated Identity REST client.
 *
 * <p>The API key is required and must be supplied by a secret source, for example the
 * {@code VG_IDENTITY_REST_CLIENT_API_KEY} environment variable. It must not be committed to an
 * application properties file.</p>
 */
@ConfigurationProperties("vg.identity.rest-client")
public class IdentityRestClientProperties {
    private String baseUrl = "http://localhost:8080";
    private String apiKey;

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
}
