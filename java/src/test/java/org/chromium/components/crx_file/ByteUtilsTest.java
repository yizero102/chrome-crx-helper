package org.chromium.components.crx_file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

class ByteUtilsTest {

    @Test
    void toLittleEndianZero() {
        byte[] result = ByteUtils.toLittleEndian(0);
        assertArrayEquals(new byte[] {0, 0, 0, 0}, result);
    }

    @Test
    void toLittleEndianOne() {
        byte[] result = ByteUtils.toLittleEndian(1);
        assertArrayEquals(new byte[] {1, 0, 0, 0}, result);
    }

    @Test
    void toLittleEndian256() {
        byte[] result = ByteUtils.toLittleEndian(256);
        assertArrayEquals(new byte[] {0, 1, 0, 0}, result);
    }

    @Test
    void toLittleEndian65536() {
        byte[] result = ByteUtils.toLittleEndian(65536);
        assertArrayEquals(new byte[] {0, 0, 1, 0}, result);
    }

    @Test
    void toLittleEndian16777216() {
        byte[] result = ByteUtils.toLittleEndian(16777216);
        assertArrayEquals(new byte[] {0, 0, 0, 1}, result);
    }

    @Test
    void toLittleEndianMaxValue() {
        byte[] result = ByteUtils.toLittleEndian(Integer.MAX_VALUE);
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F}, result);
    }

    @Test
    void toLittleEndianNegativeOne() {
        byte[] result = ByteUtils.toLittleEndian(-1);
        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF}, result);
    }

    @Test
    void toLittleEndianMinValue() {
        byte[] result = ByteUtils.toLittleEndian(Integer.MIN_VALUE);
        assertArrayEquals(new byte[] {0, 0, 0, (byte) 0x80}, result);
    }

    @Test
    void toLittleEndianArbitraryValue() {
        byte[] result = ByteUtils.toLittleEndian(0x12345678);
        assertArrayEquals(new byte[] {0x78, 0x56, 0x34, 0x12}, result);
    }

    @Test
    void fromLittleEndianZero() {
        int result = ByteUtils.fromLittleEndian(new byte[] {0, 0, 0, 0});
        assertEquals(0, result);
    }

    @Test
    void fromLittleEndianOne() {
        int result = ByteUtils.fromLittleEndian(new byte[] {1, 0, 0, 0});
        assertEquals(1, result);
    }

    @Test
    void fromLittleEndian256() {
        int result = ByteUtils.fromLittleEndian(new byte[] {0, 1, 0, 0});
        assertEquals(256, result);
    }

    @Test
    void fromLittleEndian65536() {
        int result = ByteUtils.fromLittleEndian(new byte[] {0, 0, 1, 0});
        assertEquals(65536, result);
    }

    @Test
    void fromLittleEndian16777216() {
        int result = ByteUtils.fromLittleEndian(new byte[] {0, 0, 0, 1});
        assertEquals(16777216, result);
    }

    @Test
    void fromLittleEndianMaxValue() {
        int result = ByteUtils.fromLittleEndian(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x7F});
        assertEquals(Integer.MAX_VALUE, result);
    }

    @Test
    void fromLittleEndianNegativeOne() {
        int result = ByteUtils.fromLittleEndian(new byte[] {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        assertEquals(-1, result);
    }

    @Test
    void fromLittleEndianMinValue() {
        int result = ByteUtils.fromLittleEndian(new byte[] {0, 0, 0, (byte) 0x80});
        assertEquals(Integer.MIN_VALUE, result);
    }

    @Test
    void fromLittleEndianArbitraryValue() {
        int result = ByteUtils.fromLittleEndian(new byte[] {0x78, 0x56, 0x34, 0x12});
        assertEquals(0x12345678, result);
    }

    @Test
    void fromLittleEndianThrowsOnWrongLength() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ByteUtils.fromLittleEndian(new byte[] {1, 2, 3})
        );
        assertEquals("Little-endian conversion requires exactly 4 bytes", exception.getMessage());
    }

    @Test
    void littleEndianRoundTrip() {
        int[] testValues = {0, 1, 255, 256, 65535, 65536, Integer.MAX_VALUE, Integer.MIN_VALUE, -1, -256, 0x12345678};
        for (int value : testValues) {
            byte[] bytes = ByteUtils.toLittleEndian(value);
            int result = ByteUtils.fromLittleEndian(bytes);
            assertEquals(value, result, "Round trip failed for value: " + value);
        }
    }

    @Test
    void writeLittleEndian() throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteUtils.writeLittleEndian(outputStream, 0x12345678);
        assertArrayEquals(new byte[] {0x78, 0x56, 0x34, 0x12}, outputStream.toByteArray());
    }

    @Test
    void toHexEmptyArray() {
        String result = ByteUtils.toHex(new byte[] {});
        assertEquals("", result);
    }

    @Test
    void toHexSingleByte() {
        String result = ByteUtils.toHex(new byte[] {0x12});
        assertEquals("12", result);
    }

    @Test
    void toHexMultipleBytes() {
        String result = ByteUtils.toHex(new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef});
        assertEquals("0123456789abcdef", result);
    }

    @Test
    void toHexAllZeros() {
        String result = ByteUtils.toHex(new byte[] {0, 0, 0, 0});
        assertEquals("00000000", result);
    }

    @Test
    void toHexAllOnes() {
        String result = ByteUtils.toHex(new byte[] {(byte) 0xFF, (byte) 0xFF});
        assertEquals("ffff", result);
    }

    @Test
    void fromHexEmptyString() {
        byte[] result = ByteUtils.fromHex("");
        assertArrayEquals(new byte[] {}, result);
    }

    @Test
    void fromHexSingleByte() {
        byte[] result = ByteUtils.fromHex("12");
        assertArrayEquals(new byte[] {0x12}, result);
    }

    @Test
    void fromHexMultipleBytes() {
        byte[] result = ByteUtils.fromHex("0123456789abcdef");
        assertArrayEquals(new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef}, result);
    }

    @Test
    void fromHexUppercase() {
        byte[] result = ByteUtils.fromHex("0123456789ABCDEF");
        assertArrayEquals(new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef}, result);
    }

    @Test
    void fromHexMixedCase() {
        byte[] result = ByteUtils.fromHex("0123456789AbCdEf");
        assertArrayEquals(new byte[] {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xab, (byte) 0xcd, (byte) 0xef}, result);
    }

    @Test
    void fromHexThrowsOnOddLength() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ByteUtils.fromHex("123")
        );
        assertEquals("Hex string must have an even length", exception.getMessage());
    }

    @Test
    void fromHexThrowsOnInvalidCharacter() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ByteUtils.fromHex("12xy")
        );
        assertTrue(exception.getMessage().contains("Invalid hexadecimal character"));
    }

    @Test
    void hexRoundTrip() {
        byte[] original = new byte[] {0x00, 0x12, 0x34, 0x56, 0x78, (byte) 0x9a, (byte) 0xbc, (byte) 0xde, (byte) 0xff};
        String hex = ByteUtils.toHex(original);
        byte[] result = ByteUtils.fromHex(hex);
        assertArrayEquals(original, result);
    }

    @Test
    void containsSubsequenceEmptySubsequence() {
        byte[] array = new byte[] {1, 2, 3};
        byte[] subsequence = new byte[] {};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceAtStart() {
        byte[] array = new byte[] {1, 2, 3, 4, 5};
        byte[] subsequence = new byte[] {1, 2};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceAtEnd() {
        byte[] array = new byte[] {1, 2, 3, 4, 5};
        byte[] subsequence = new byte[] {4, 5};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceInMiddle() {
        byte[] array = new byte[] {1, 2, 3, 4, 5};
        byte[] subsequence = new byte[] {2, 3, 4};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceExactMatch() {
        byte[] array = new byte[] {1, 2, 3};
        byte[] subsequence = new byte[] {1, 2, 3};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceNotFound() {
        byte[] array = new byte[] {1, 2, 3, 4, 5};
        byte[] subsequence = new byte[] {2, 4};
        assertFalse(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceSubsequenceTooLong() {
        byte[] array = new byte[] {1, 2, 3};
        byte[] subsequence = new byte[] {1, 2, 3, 4};
        assertFalse(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceEmptyArray() {
        byte[] array = new byte[] {};
        byte[] subsequence = new byte[] {1};
        assertFalse(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequencePartialMatch() {
        byte[] array = new byte[] {1, 2, 3, 2, 4, 5};
        byte[] subsequence = new byte[] {2, 4};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceMultipleOccurrences() {
        byte[] array = new byte[] {1, 2, 3, 2, 3, 4};
        byte[] subsequence = new byte[] {2, 3};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceZipEocd() {
        byte[] array = new byte[] {0x50, 0x4b, 0x05, 0x06};
        byte[] subsequence = new byte[] {0x50, 0x4b, 0x05, 0x06};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceZipEocdInLargerArray() {
        byte[] array = new byte[] {1, 2, 0x50, 0x4b, 0x05, 0x06, 7, 8};
        byte[] subsequence = new byte[] {0x50, 0x4b, 0x05, 0x06};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceNoMatchSimilarPattern() {
        byte[] array = new byte[] {1, 2, 3, 1, 3, 4};
        byte[] subsequence = new byte[] {1, 2, 4};
        assertFalse(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceSingleByteMatch() {
        byte[] array = new byte[] {1, 2, 3, 4, 5};
        byte[] subsequence = new byte[] {3};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceSingleByteNoMatch() {
        byte[] array = new byte[] {1, 2, 3, 4, 5};
        byte[] subsequence = new byte[] {6};
        assertFalse(ByteUtils.containsSubsequence(array, subsequence));
    }

    @Test
    void containsSubsequenceNegativeBytes() {
        byte[] array = new byte[] {(byte) 0xFF, (byte) 0xFE, (byte) 0xFD};
        byte[] subsequence = new byte[] {(byte) 0xFE, (byte) 0xFD};
        assertTrue(ByteUtils.containsSubsequence(array, subsequence));
    }
}
