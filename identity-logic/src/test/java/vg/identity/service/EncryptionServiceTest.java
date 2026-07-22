package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vg.identity.EncryptionProperties;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    // Base64-encoded 32-byte AES-256 keys.
    private static final String KEY = "dGVzdC1zZWNyZXQta2V5LTEyMzQ1Njc4OTAxMjM0NTY=";
    private static final String OTHER_KEY = "YW5vdGhlci1zZWNyZXQta2V5LTA5ODc2NTQzMjF6eXg=";
    private static final String NEW_KEY = "bmV3LWN1cnJlbnQta2V5LWFiY2RlZmdoaWprbG1ub3A=";
    // Base64 of 16 bytes -> wrong length for AES-256.
    private static final String SHORT_KEY = "c2l4dGVlbi1ieXRlLWtleQ==";

    private static final String BLIND_INDEX_KEY = "test-blind-index-key";

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(properties(1, Map.of(1, KEY)));
    }

    private static EncryptionProperties properties(Integer currentKeyId, Map<Integer, String> keys) {
        return properties(BLIND_INDEX_KEY, currentKeyId, keys);
    }

    private static EncryptionProperties properties(String blindIndexKey, Integer currentKeyId, Map<Integer, String> keys) {
        var properties = new EncryptionProperties();
        properties.setBlindIndexKey(blindIndexKey);
        properties.setCurrentKeyId(currentKeyId);
        properties.setKeys(keys);
        return properties;
    }

    @Test
    void encode_whenInputIsNull_returnsNull() {
        assertThat(encryptionService.encode(null)).isNull();
    }

    @Test
    void encode_whenInputIsEmpty_returnsEncodedValue() {
        var plaintext = "";
        var encoded = encryptionService.encode(plaintext);
        assertThat(encoded).isNotNull();
        assertThat(encryptionService.decode(encoded)).isEqualTo("");
    }

    @Test
    void decode_whenInputIsNull_returnsNull() {
        assertThat(encryptionService.decode(null)).isNull();
    }

    @Test
    void encodeAndDecode_whenInputIsValid_returnsOriginalString() {
        var plaintext = "Hello, World! " + UUID.randomUUID();
        var encoded = encryptionService.encode(plaintext);

        assertThat(encoded).isNotNull();
        assertThat(new String(encoded)).isNotEqualTo(plaintext);

        var decoded = encryptionService.decode(encoded);
        assertThat(decoded).isEqualTo(plaintext);
    }

    @Test
    void encode_stampsCurrentKeyIdAsFirstByte() {
        var encoded = encryptionService.encode("payload");

        assertThat(encoded[0]).isEqualTo((byte) 1);
    }

    @Test
    void encode_whenSameInputIsEncodedTwice_returnsDifferentResults() {
        var plaintext = "same-input";
        var encoded1 = encryptionService.encode(plaintext);
        var encoded2 = encryptionService.encode(plaintext);

        assertThat(encoded1).isNotEqualTo(encoded2);
        assertThat(encryptionService.decode(encoded1)).isEqualTo(plaintext);
        assertThat(encryptionService.decode(encoded2)).isEqualTo(plaintext);
    }

    @Test
    void decode_whenDataIsInvalid_throwsException() {
        assertThatThrownBy(() -> encryptionService.decode(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt field value");
    }

    @Test
    void constructor_whenPropertiesAreNull_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructor_whenBlindIndexKeyIsMissing_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(properties(" ", 1, Map.of(1, KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blind-index-key");
    }

    @Test
    void constructor_whenKeysAreMissing_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(properties(1, Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("keys");
    }

    @Test
    void constructor_whenCurrentKeyIdIsMissing_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(properties(null, Map.of(1, KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current-key-id");
    }

    @Test
    void constructor_whenCurrentKeyIdIsNotInKeyring_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(properties(2, Map.of(1, KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("current-key-id");
    }

    @Test
    void constructor_whenKeyIdIsOutOfRange_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(properties(256, Map.of(256, KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("range");
    }

    @Test
    void constructor_whenKeyIsNotValidBase64_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(properties(1, Map.of(1, "not valid base64 !!!"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void constructor_whenKeyHasWrongLength_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(properties(1, Map.of(1, SHORT_KEY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void decode_whenKeyIsDifferent_throwsException() {
        var plaintext = "secret-data";
        var encoded = encryptionService.encode(plaintext);

        var otherService = new EncryptionService(properties(1, Map.of(1, OTHER_KEY)));

        assertThatThrownBy(() -> otherService.decode(encoded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt field value");
    }

    @Test
    void decode_whenKeyIdIsUnknown_throwsException() {
        // encoded by a service whose current key is id 2
        var producer = new EncryptionService(properties(2, Map.of(2, KEY)));
        var encoded = producer.encode("secret-data");

        // the default service only knows key id 1
        assertThatThrownBy(() -> encryptionService.decode(encoded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt field value");
    }

    @Test
    void rotation_whenNewKeyIsCurrent_stillDecodesDataWrittenWithOldKey() {
        var oldService = new EncryptionService(properties(1, Map.of(1, KEY)));
        var encodedWithOldKey = oldService.encode("legacy-value");

        // rotation: key 2 becomes current, key 1 retained on the keyring for reads
        var rotatedService = new EncryptionService(properties(2, Map.of(1, KEY, 2, NEW_KEY)));

        assertThat(rotatedService.decode(encodedWithOldKey)).isEqualTo("legacy-value");

        var encodedWithNewKey = rotatedService.encode("fresh-value");
        assertThat(encodedWithNewKey[0]).isEqualTo((byte) 2);
        assertThat(rotatedService.decode(encodedWithNewKey)).isEqualTo("fresh-value");
    }

    @Test
    void hash_whenInputIsValid_returnsHash() {
        var input = "test-input";
        var hash1 = encryptionService.hash(input);
        var hash2 = encryptionService.hash(input);

        assertThat(hash1).isNotNull();
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1.length).isEqualTo(32); // SHA-256 is 256 bits = 32 bytes
    }

    @Test
    void hash_whenInputIsNull_returnsNull() {
        assertThat(encryptionService.hash(null)).isNull();
    }

    @Test
    void hash_whenBlindIndexKeyIsDifferent_returnsDifferentHashes() {
        var input = "test-input";
        var hash1 = encryptionService.hash(input);

        var otherService = new EncryptionService(properties("different-blind-index-key", 1, Map.of(1, KEY)));
        var hash2 = otherService.hash(input);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void canonicalizeAndHash_whenInputHasCaseAndSpaces_returnsCanonicalHash() {
        var input1 = "  UserInput  ";
        var input2 = "userinput";

        var hash1 = encryptionService.canonicalizeAndHash(input1);
        var hash2 = encryptionService.canonicalizeAndHash(input2);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashCaseSensitive_whenInputCaseDiffers_returnsDifferentHashes() {
        var input1 = "UserInput";
        var input2 = "userinput";

        var hash1 = encryptionService.hashCaseSensitive(input1);
        var hash2 = encryptionService.hashCaseSensitive(input2);

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
