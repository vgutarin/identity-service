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
        EncryptionProperties properties = new EncryptionProperties();
        properties.setSecret("test-secret-key-1234567890123456");
        properties.setSalt("test-salt");
        encryptionService = new EncryptionService(properties);
    }

    @Test
    void encode_whenInputIsNull_returnsNull() {
        assertThat(encryptionService.encode(null)).isNull();
    }

    @Test
    void encode_withEmptyString_works() {
        String plaintext = "";
        byte[] encoded = encryptionService.encode(plaintext);
        assertThat(encoded).isNotNull();
        assertThat(encryptionService.decode(encoded)).isEqualTo("");
    }

    @Test
    void decode_whenInputIsNull_returnsNull() {
        assertThat(encryptionService.decode(null)).isNull();
    }

    @Test
    void encodeAndDecode_returnsOriginalString() {
        String plaintext = "Hello, World! " + UUID.randomUUID();
        byte[] encoded = encryptionService.encode(plaintext);

        assertThat(encoded).isNotNull();
        assertThat(new String(encoded)).isNotEqualTo(plaintext);

        String decoded = encryptionService.decode(encoded);
        assertThat(decoded).isEqualTo(plaintext);
    }

    @Test
    void encode_producesDifferentResultsForSameInput() {
        String plaintext = "same-input";
        byte[] encoded1 = encryptionService.encode(plaintext);
        byte[] encoded2 = encryptionService.encode(plaintext);

        assertThat(encoded1).isNotEqualTo(encoded2);
        assertThat(encryptionService.decode(encoded1)).isEqualTo(plaintext);
        assertThat(encryptionService.decode(encoded2)).isEqualTo(plaintext);
    }

    @Test
    void decode_withInvalidData_throwsException() {
        assertThatThrownBy(() -> encryptionService.decode(new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt field value");
    }

    @Test
    void constructor_withNullProperties_throwsException() {
        assertThatThrownBy(() -> new EncryptionService(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void decode_withDifferentSecret_fails() {
        String plaintext = "secret-data";
        byte[] encoded = encryptionService.encode(plaintext);

        EncryptionProperties otherProperties = new EncryptionProperties();
        otherProperties.setSecret("different-secret-key");
        otherProperties.setSalt("test-salt");
        EncryptionService otherService = new EncryptionService(otherProperties);

        assertThatThrownBy(() -> otherService.decode(encoded))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to decrypt field value");
    }
}
