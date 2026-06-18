package vg.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vg.identity.EncryptionProperties;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        var properties = new EncryptionProperties();
        properties.setSecret("test-secret-key-1234567890123456");
        properties.setSalt("test-salt");
        encryptionService = new EncryptionService(properties);
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
    void decode_whenSecretIsDifferent_throwsException() {
        var plaintext = "secret-data";
        var encoded = encryptionService.encode(plaintext);

        var otherProperties = new EncryptionProperties();
        otherProperties.setSecret("different-secret-key");
        otherProperties.setSalt("test-salt");
        var otherService = new EncryptionService(otherProperties);

        assertThatThrownBy(() -> otherService.decode(encoded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt field value");
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
    void hash_whenInputIsValid_usesSalt() {
        var input = "test-input";
        var hash1 = encryptionService.hash(input);

        var otherProperties = new EncryptionProperties();
        otherProperties.setSecret("test-secret-key-1234567890123456");
        otherProperties.setSalt("different-salt");
        var otherService = new EncryptionService(otherProperties);
        var hash2 = otherService.hash(input);

        // This is expected to FAIL before the fix, and PASS after the fix
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
