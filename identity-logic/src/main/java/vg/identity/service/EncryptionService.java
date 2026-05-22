package vg.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vg.identity.EncryptionProperties;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@Component
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 65536;
    private static final int KEY_LENGTH_BITS = 256;

    private final SecretKey secretKey;

    public EncryptionService(EncryptionProperties properties) {
        this.secretKey = deriveKey(properties.getSecret(), properties.getSalt());
    }

    public byte[] encode(String src) {
        if (src == null) {
            return null;
        }
        try {
            var iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            var encrypted = cipher.doFinal(src.getBytes(UTF_8));

            var result = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, result, IV_LENGTH, encrypted.length);

            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt field value", e);
        }
    }

    public String decode(byte[] encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            var iv = Arrays.copyOfRange(encoded, 0, IV_LENGTH);
            var ciphertext = Arrays.copyOfRange(encoded, IV_LENGTH, encoded.length);

            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));

            return new String(cipher.doFinal(ciphertext), UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt field value", e);
        }
    }

    private static SecretKey deriveKey(String secret, String salt) {
        try {
            var factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            var spec = new PBEKeySpec(secret.toCharArray(), salt.getBytes(UTF_8), PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
            var keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive encryption key", e);
        }
    }
}
