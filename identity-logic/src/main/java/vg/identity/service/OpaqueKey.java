package vg.identity.service;

import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/**
 * Generates high-entropy opaque secrets and assembles them into credential keys.
 * <p>
 * {@link #newSecret} produces random bytes that are only ever persisted as their SHA-256 hash ({@link #sha256});
 * {@link #secretMatches} verifies a presented secret against that stored hash in constant time. {@link #format}
 * packages a secret with a lookup id as {@code <id><separator><secret>}, base64url-encoding the secret without
 * padding.
 * <p>
 * The encoded secret always has a fixed length, so {@link #parse} splits from the right at a known offset rather
 * than scanning for the separator; this stays unambiguous even when the separator character also belongs to the
 * base64url alphabet. The id half is left opaque: {@link #parse} returns it as a raw string, and each caller parses
 * it into its own type (for example {@code UUID} or {@code Long}) and applies its own id validation.
 */
final class OpaqueKey {

    private static final int SECRET_BYTES = 32;
    private static final int ENCODED_SECRET_LENGTH = 43; // 32 bytes, base64url, no padding
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private OpaqueKey() {
    }

    static byte[] newSecret() {
        var secret = new byte[SECRET_BYTES];
        SECURE_RANDOM.nextBytes(secret);
        return secret;
    }

    static byte[] sha256(byte[] secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static boolean secretMatches(byte[] expectedHash, byte[] secret) {
        return expectedHash != null && secret != null
                && MessageDigest.isEqual(expectedHash, sha256(secret));
    }

    static String format(String id, char separator, byte[] secret) {
        return id + separator + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    static Optional<Parsed> parse(String value, char separator) {
        if (!StringUtils.hasText(value) || value.length() < ENCODED_SECRET_LENGTH + 2) {
            return Optional.empty();
        }

        var separatorIndex = value.length() - ENCODED_SECRET_LENGTH - 1;
        if (value.charAt(separatorIndex) != separator) {
            return Optional.empty();
        }

        try {
            var secret = Base64.getUrlDecoder().decode(value.substring(separatorIndex + 1));
            return secret.length == SECRET_BYTES
                    ? Optional.of(new Parsed(value.substring(0, separatorIndex), secret))
                    : Optional.empty();
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    record Parsed(String id, byte[] secret) {
    }
}
