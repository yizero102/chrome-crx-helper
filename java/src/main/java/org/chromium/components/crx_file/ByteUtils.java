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
