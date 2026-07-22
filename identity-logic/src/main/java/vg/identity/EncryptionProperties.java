package vg.identity;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties("identity.encryption")
public class EncryptionProperties {

    /**
     * Secret HMAC-SHA256 key backing the deterministic blind index (used for equality lookups and
     * unique constraints over encrypted columns). Its confidentiality is the only thing preventing an
     * attacker with a database dump from brute-forcing the hashed values, so it must be high-entropy
     * and secret. Kept separate from {@link #keys} because it rotates independently (and rotating it
     * is a harder, migration-heavy operation).
     */
    private String blindIndexKey;

    /**
     * Id of the key that encrypts new data. Must be present in {@link #keys} and in range 0..255.
     * To rotate: add a new entry to {@link #keys} and point this at it. Old entries are kept so
     * data written with them stays readable until it is re-encrypted.
     */
    private Integer currentKeyId;

    /**
     * Keyring: key id -> Base64-encoded 32-byte AES-256 key (e.g. {@code openssl rand -base64 32}).
     * The value is the raw key material, used directly with no derivation. Every ciphertext is stamped
     * with the id of the key that produced it, so multiple keys can coexist during rotation.
     */
    private Map<Integer, String> keys = new HashMap<>();
}
