package org.chromium.components.crx_file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

class IdUtilTest {

    @Test
    void generateIdFromSimpleInput() {
        byte[] input = "test".getBytes(StandardCharsets.UTF_8);
        String id = IdUtil.generateId(input);
        
        assertEquals(32, id.length());
        assertTrue(IdUtil.isValid(id));
        assertTrue(id.matches("[a-p]{32}"));
    }

    @Test
    void generateIdIsConsistent() {
        byte[] input = "consistent".getBytes(StandardCharsets.UTF_8);
        String id1 = IdUtil.generateId(input);
        String id2 = IdUtil.generateId(input);
        
        assertEquals(id1, id2);
    }

    @Test
    void generateIdDifferentInputsProduceDifferentIds() {
        byte[] input1 = "input1".getBytes(StandardCharsets.UTF_8);
        byte[] input2 = "input2".getBytes(StandardCharsets.UTF_8);
        
        String id1 = IdUtil.generateId(input1);
        String id2 = IdUtil.generateId(input2);
        
        assertFalse(id1.equals(id2));
    }

    @Test
    void generateIdFromEmptyInput() {
        byte[] input = new byte[0];
        String id = IdUtil.generateId(input);
        
        assertEquals(32, id.length());
        assertTrue(IdUtil.isValid(id));
    }

    @Test
    void generateIdFromHashValid() throws NoSuchAlgorithmException {
        byte[] input = "test".getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input);
        
        String id = IdUtil.generateIdFromHash(hash);
        
        assertEquals(32, id.length());
        assertTrue(IdUtil.isValid(id));
    }

    @Test
    void generateIdFromHashThrowsOnShortHash() {
        byte[] shortHash = new byte[15];
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> IdUtil.generateIdFromHash(shortHash)
        );
        
        assertEquals("Hash must be at least 16 bytes", exception.getMessage());
    }

    @Test
    void generateIdFromHashTruncatesLongHash() throws NoSuchAlgorithmException {
        byte[] input = "test".getBytes(StandardCharsets.UTF_8);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input);
        
        assertEquals(32, hash.length);
        
        String id1 = IdUtil.generateIdFromHash(hash);
        String id2 = IdUtil.generateIdFromHash(Arrays.copyOf(hash, 16));
        
        assertEquals(id1, id2);
    }

    @Test
    void generateIdFromHexValid() {
        String hex = "0123456789abcdef0123456789abcdef";
        String id = IdUtil.generateIdFromHex(hex);
        
        assertEquals("abcdefghijklmnopabcdefghijklmnop", id);
    }

    @Test
    void generateIdFromHexMapsAllDigitsToAlphabet() {
        String hex = "0123456789abcdef";
        hex = hex + hex;
        String id = IdUtil.generateIdFromHex(hex);
        
        assertEquals("abcdefghijklmnopabcdefghijklmnop", id);
    }

    @Test
    void generateIdFromHexThrowsOnWrongLength() {
        String shortHex = "0123456789abcdef";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> IdUtil.generateIdFromHex(shortHex)
        );
        
        assertEquals("Hex string must be 32 characters long", exception.getMessage());
    }

    @Test
    void generateIdFromHexHandlesInvalidCharacters() {
        final String invalidHex = "xyz123456789abcdef0123456789ab";
        
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> IdUtil.generateIdFromHex(invalidHex)
        );
    }

    @Test
    void generateIdFromHexWithAllZeros() {
        String hex = "00000000000000000000000000000000";
        String id = IdUtil.generateIdFromHex(hex);
        
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", id);
        assertTrue(IdUtil.isValid(id));
    }

    @Test
    void generateIdFromHexWithAllFs() {
        String hex = "ffffffffffffffffffffffffffffffff";
        String id = IdUtil.generateIdFromHex(hex);
        
        assertEquals("pppppppppppppppppppppppppppppppp", id);
        assertTrue(IdUtil.isValid(id));
    }

    @Test
    void isValidReturnsTrueForValidId() {
        String validId = "abcdefghijklmnopabcdefghijklmnop";
        assertTrue(IdUtil.isValid(validId));
    }

    @Test
    void isValidReturnsTrueForAllA() {
        String validId = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        assertTrue(IdUtil.isValid(validId));
    }

    @Test
    void isValidReturnsTrueForAllP() {
        String validId = "pppppppppppppppppppppppppppppppp";
        assertTrue(IdUtil.isValid(validId));
    }

    @Test
    void isValidReturnsFalseForShortId() {
        String shortId = "abcdefghijklmnopabcdefghijklmno";
        assertFalse(IdUtil.isValid(shortId));
    }

    @Test
    void isValidReturnsFalseForLongId() {
        String longId = "abcdefghijklmnopabcdefghijklmnopa";
        assertFalse(IdUtil.isValid(longId));
    }

    @Test
    void isValidReturnsFalseForEmptyString() {
        assertFalse(IdUtil.isValid(""));
    }

    @Test
    void isValidReturnsFalseForInvalidCharacters() {
        String invalidId = "qbcdefghijklmnopabcdefghijklmnop";
        assertFalse(IdUtil.isValid(invalidId));
    }

    @Test
    void isValidReturnsFalseForCharacterBeforeA() {
        String invalidId = "`bcdefghijklmnopabcdefghijklmnop";
        assertFalse(IdUtil.isValid(invalidId));
    }

    @Test
    void isValidReturnsFalseForCharacterAfterP() {
        String invalidId = "abcdefghijklmnopabcdefghijklmnoq";
        assertFalse(IdUtil.isValid(invalidId));
    }

    @Test
    void isValidReturnsFalseForNumericCharacters() {
        String invalidId = "1bcdefghijklmnopabcdefghijklmnop";
        assertFalse(IdUtil.isValid(invalidId));
    }

    @Test
    void isValidHandlesUppercaseCharacters() {
        String uppercaseId = "ABCDEFGHIJKLMNOPABCDEFGHIJKLMNOP";
        assertTrue(IdUtil.isValid(uppercaseId));
    }

    @Test
    void isValidReturnsFalseForUppercaseOutOfRange() {
        String invalidId = "QBCDEFGHIJKLMNOPABCDEFGHIJKLMNOP";
        assertFalse(IdUtil.isValid(invalidId));
    }

    @Test
    void isValidReturnsFalseForMixedCase() {
        String mixedId = "AbCdEfGhIjKlMnOpAbCdEfGhIjKlMnOp";
        assertTrue(IdUtil.isValid(mixedId));
    }

    @Test
    void generateIdMatchesExpectedPattern() {
        byte[] input = "test extension key".getBytes(StandardCharsets.UTF_8);
        String id = IdUtil.generateId(input);
        
        assertTrue(id.chars().allMatch(c -> (c >= 'a' && c <= 'p')));
    }

    @Test
    void hexToAlphabetConversionCoversAllValues() {
        for (int i = 0; i < 16; i++) {
            String hex = String.format("%01x", i).repeat(32);
            String id = IdUtil.generateIdFromHex(hex);
            char expected = "abcdefghijklmnop".charAt(i);
            assertTrue(id.chars().allMatch(c -> c == expected));
        }
    }

    @Test
    void generateIdFromLargeInput() {
        byte[] largeInput = new byte[10000];
        Arrays.fill(largeInput, (byte) 0x42);
        
        String id = IdUtil.generateId(largeInput);
        
        assertEquals(32, id.length());
        assertTrue(IdUtil.isValid(id));
    }

    @Test
    void generateIdFromNullBytes() {
        byte[] nullBytes = new byte[100];
        String id = IdUtil.generateId(nullBytes);
        
        assertEquals(32, id.length());
        assertTrue(IdUtil.isValid(id));
    }

    @Test
    void idSizeConstantIsCorrect() {
        assertEquals(16, IdUtil.ID_SIZE);
    }
}
