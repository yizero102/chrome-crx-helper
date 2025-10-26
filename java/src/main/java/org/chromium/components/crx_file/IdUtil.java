package org.chromium.components.crx_file;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public final class IdUtil {
    public static final int ID_SIZE = 16;
    private static final char[] ID_ALPHABET = "abcdefghijklmnop".toCharArray();

    private IdUtil() {}

    public static String generateId(byte[] input) {
        return generateIdFromHash(sha256(input));
    }

    public static String generateIdFromHash(byte[] hash) {
        if (hash.length < ID_SIZE) {
            throw new IllegalArgumentException("Hash must be at least 16 bytes");
        }
        byte[] truncated = Arrays.copyOf(hash, ID_SIZE);
        return generateIdFromHex(ByteUtils.toHex(truncated));
    }

    public static String generateIdFromHex(String hex) {
        if (hex.length() != ID_SIZE * 2) {
            throw new IllegalArgumentException("Hex string must be 32 characters long");
        }
        return convertHexToAlphabet(hex);
    }

    public static boolean isValid(String id) {
        if (id.length() != ID_SIZE * 2) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char ch = Character.toLowerCase(id.charAt(i));
            if (ch < 'a' || ch > 'p') {
                return false;
            }
        }
        return true;
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private static String convertHexToAlphabet(String hex) {
        StringBuilder builder = new StringBuilder(hex.length());
        for (int i = 0; i < hex.length(); i++) {
            int value = Character.digit(hex.charAt(i), 16);
            if (value < 0) {
                builder.append('a');
            } else {
                builder.append(ID_ALPHABET[value]);
            }
        }
        return builder.toString();
    }
}
