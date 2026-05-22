package vg.identity.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {

    public static byte[] sha256(String input) {
        if (input == null) {
            return null;
        }
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not found", e);
        }
    }

    public static byte[] canonicalizeAndHash(String input) {
        if (input == null) {
            return null;
        }
        return sha256(input.toLowerCase().trim());
    }

    public static byte[] hashCaseSensitive(String input) {
        if (input == null) {
            return null;
        }
        return sha256(input);
    }
}
