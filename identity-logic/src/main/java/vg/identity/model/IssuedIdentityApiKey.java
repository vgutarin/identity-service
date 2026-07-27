package vg.identity.model;

/**
 * Result of issuing an API key. The raw value is intentionally available only in this one-time result.
 */
public record IssuedIdentityApiKey(IdentityApiKey apiKey, String value) {
    @Override
    public String toString() {
        return "IssuedIdentityApiKey[apiKey=" + apiKey + ", value=[redacted]]";
    }
}
