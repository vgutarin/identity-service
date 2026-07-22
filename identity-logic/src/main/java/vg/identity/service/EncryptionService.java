package vg.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vg.identity.EncryptionProperties;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Component
public class EncryptionService {

    private static final String ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding";
    private static final int KEY_ID_LENGTH = 1;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int AES_KEY_LENGTH_BYTES = 32;
    private static final int MAX_KEY_ID = 0xFF;

    private static final String HASH_ALGORITHM = "HmacSHA256";

    private final Map<Integer, SecretKey> keyring;
    private final int currentKeyId;
    private final SecretKey currentKey;
    private final SecretKey blindIndexKey;

    public EncryptionService(EncryptionProperties properties) {
        if (properties.getBlindIndexKey() == null || properties.getBlindIndexKey().isBlank()) {
            throw new IllegalStateException("identity.encryption.blind-index-key is not configured!");
        }
        if (properties.getKeys() == null || properties.getKeys().isEmpty()) {
            throw new IllegalStateException("identity.encryption.keys is not configured!");
        }
        if (properties.getCurrentKeyId() == null) {
            throw new IllegalStateException("identity.encryption.current-key-id is not configured!");
        }

        var ring = new HashMap<Integer, SecretKey>();
        properties.getKeys().forEach((id, key) -> {
            if (id < 0 || id > MAX_KEY_ID) {
                throw new IllegalStateException("Encryption key id must be in range 0.." + MAX_KEY_ID + ", got: " + id);
            }
            ring.put(id, parseKey(id, key));
        });

        this.currentKeyId = properties.getCurrentKeyId();
        if (!ring.containsKey(currentKeyId)) {
            throw new IllegalStateException("identity.encryption.current-key-id " + currentKeyId + " is not present in identity.encryption.keys");
        }

        this.keyring = Map.copyOf(ring);
        this.currentKey = this.keyring.get(currentKeyId);
        this.blindIndexKey = new SecretKeySpec(properties.getBlindIndexKey().getBytes(UTF_8), HASH_ALGORITHM);
    }

    public byte[] encode(String src) {
        if (src == null) {
            return null;
        }
        try {
            var iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            var cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, currentKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            var encrypted = cipher.doFinal(src.getBytes(UTF_8));

            return ByteBuffer.allocate(KEY_ID_LENGTH + iv.length + encrypted.length)
                    .put((byte) currentKeyId)
                    .put(iv)
                    .put(encrypted)
                    .array();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt field value", e);
        }
    }

    public String decode(byte[] encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            var keyId = encoded[0] & 0xFF;
            var key = keyring.get(keyId);
            if (key == null) {
                throw new IllegalStateException("No encryption key configured for key id " + keyId);
            }

            var iv = Arrays.copyOfRange(encoded, KEY_ID_LENGTH, KEY_ID_LENGTH + IV_LENGTH);
            var ciphertext = Arrays.copyOfRange(encoded, KEY_ID_LENGTH + IV_LENGTH, encoded.length);

            var cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(ciphertext), UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt field value", e);
        }
    }

    public byte[] hash(String input) {
        if (input == null) {
            return null;
        }

        try {
            var mac = Mac.getInstance(HASH_ALGORITHM);
            mac.init(blindIndexKey);
            return mac.doFinal(input.getBytes(UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Error during hash generating", e);
        }
    }

    public byte[] canonicalizeAndHash(String input) {
        if (input == null) {
            return null;
        }
        return hash(canonicalize(input));
    }

    public byte[] hashCaseSensitive(String input) {
        if (input == null) {
            return null;
        }
        return hash(input);
    }

    /**
     * Blind-index hash for the principal-level {@code name}. Applies the same minimal processing on
     * both write and lookup: non-printable characters are stripped, but no case folding or other
     * canonicalization is performed. Deciding how to canonicalize the value (e.g. lower-casing) is the
     * responsibility of the concrete principal type (user, application) before it is handed to the
     * principal, so the shared principal layer never second-guesses it.
     */
    public byte[] hashPrincipalName(String input) {
        if (input == null) {
            return null;
        }
        return hash(normalizePrincipalName(input));
    }

    String canonicalize(String input) {
        if (input == null) {
            return null;
        }
        return input.toLowerCase(Locale.ROOT).trim();
    }

    private String normalizePrincipalName(String input) {
        if (input == null) {
            return null;
        }
        // Remove control/format/surrogate/unassigned/private-use code points (Unicode category "C"),
        // then strip leading/trailing (Unicode) whitespace. No case folding or other canonicalization
        // is applied: that is the principal subtype's responsibility before the value reaches here.
        return input.replaceAll("\\p{C}", "").strip();
    }

    private static SecretKey parseKey(int id, String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("Encryption key id " + id + " is not configured!");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Encryption key id " + id + " is not valid Base64", e);
        }
        if (raw.length != AES_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "Encryption key id " + id + " must decode to " + AES_KEY_LENGTH_BYTES + " bytes, got " + raw.length);
        }
        return new SecretKeySpec(raw, "AES");
    }
}
