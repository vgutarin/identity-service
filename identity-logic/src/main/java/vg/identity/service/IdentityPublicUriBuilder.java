package vg.identity.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/** Builds absolute URIs against Identity's externally reachable public origin. */
@Component
public class IdentityPublicUriBuilder {
    private final String publicUrl;

    public IdentityPublicUriBuilder(@Value("${identity.service.public-url:}") String publicUrl) {
        this.publicUrl = publicUrl;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(publicUrl);
    }

    public URI build(String path) {
        if (!isConfigured()) {
            throw new IllegalStateException("identity.service.public-url is not configured");
        }
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Public URI path must start with '/'");
        }
        return UriComponentsBuilder.fromUriString(publicUrl.trim().replaceAll("/+$", ""))
                .path(path)
                .build()
                .toUri();
    }
}
