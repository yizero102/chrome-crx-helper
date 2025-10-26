package org.chromium.components.crx_file;

import java.io.IOException;
import java.io.OutputStream;

final class ByteUtils {
    private ByteUtils() {}

    static byte[] toLittleEndian(int value) {
        return new byte[] {
                (byte) (value & 0xFF),
                (byte) ((value >>> 8) & 0xFF),
                (byte) ((value >>> 16) & 0xFF),
                (byte) ((value >>> 24) & 0xFF)
        };
    }

    static int fromLittleEndian(byte[] bytes) {
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Little-endian conversion requires exactly 4 bytes");
        }
        return (bytes[0] & 0xFF)
                | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16)
                | ((bytes[3] & 0xFF) << 24);
    }

    static void writeLittleEndian(OutputStream outputStream, int value) throws IOException {
        outputStream.write(toLittleEndian(value));
    }

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    static String toHex(byte[] input) {
        StringBuilder builder = new StringBuilder(input.length * 2);
        for (byte b : input) {
            builder.append(HEX_DIGITS[(b >>> 4) & 0xF]);
            builder.append(HEX_DIGITS[b & 0xF]);
        }
        return builder.toString();
    }

    static byte[] fromHex(String hex) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Hex string must have an even length");
        }
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hexadecimal character in: " + hex);
            }
            result[i / 2] = (byte) ((high << 4) + low);
        }
        return result;
    }

    static boolean containsSubsequence(byte[] array, byte[] subsequence) {
        if (subsequence.length == 0) {
            return true;
        }
        if (array.length < subsequence.length) {
            return false;
        }
        outer:
        for (int i = 0; i <= array.length - subsequence.length; i++) {
            for (int j = 0; j < subsequence.length; j++) {
                if (array[i + j] != subsequence[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
