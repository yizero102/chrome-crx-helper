package org.chromium.components.crx_file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

class CrxConstantsTest {

    @Test
    void magicFullMatchesCppDefinition() {
        byte[] expected = new byte[] {'C', 'r', '2', '4'};
        assertArrayEquals(expected, CrxConstants.MAGIC_FULL);
        assertEquals(4, CrxConstants.MAGIC_FULL.length);
    }

    @Test
    void magicDiffMatchesCppDefinition() {
        byte[] expected = new byte[] {'C', 'r', 'O', 'D'};
        assertArrayEquals(expected, CrxConstants.MAGIC_DIFF);
        assertEquals(4, CrxConstants.MAGIC_DIFF.length);
    }

    @Test
    void crx3VersionMatchesCppDefinition() {
        assertEquals(3, CrxConstants.CRX3_VERSION);
    }

    @Test
    void signatureContextMatchesCppDefinition() {
        String expectedString = "CRX3 SignedData\0";
        byte[] expected = expectedString.getBytes(StandardCharsets.US_ASCII);
        
        assertArrayEquals(expected, CrxConstants.SIGNATURE_CONTEXT);
        assertEquals(16, CrxConstants.SIGNATURE_CONTEXT.length);
        
        assertEquals(0, CrxConstants.SIGNATURE_CONTEXT[15]);
    }

    @Test
    void signatureContextContainsNullTerminator() {
        byte lastByte = CrxConstants.SIGNATURE_CONTEXT[CrxConstants.SIGNATURE_CONTEXT.length - 1];
        assertEquals(0, lastByte);
    }

    @Test
    void signatureContextReadablePrefix() {
        byte[] prefix = new byte[15];
        System.arraycopy(CrxConstants.SIGNATURE_CONTEXT, 0, prefix, 0, 15);
        String prefixString = new String(prefix, StandardCharsets.US_ASCII);
        assertEquals("CRX3 SignedData", prefixString);
    }

    @Test
    void zipEocdMatchesPkSignature() {
        byte[] expected = new byte[] {'P', 'K', 0x05, 0x06};
        assertArrayEquals(expected, CrxConstants.ZIP_EOCD);
        assertEquals(4, CrxConstants.ZIP_EOCD.length);
    }

    @Test
    void zipEocd64MatchesPkSignature() {
        byte[] expected = new byte[] {'P', 'K', 0x06, 0x07};
        assertArrayEquals(expected, CrxConstants.ZIP_EOCD64);
        assertEquals(4, CrxConstants.ZIP_EOCD64.length);
    }

    @Test
    void magicFullIsDistinctFromMagicDiff() {
        boolean different = false;
        for (int i = 0; i < CrxConstants.MAGIC_FULL.length; i++) {
            if (CrxConstants.MAGIC_FULL[i] != CrxConstants.MAGIC_DIFF[i]) {
                different = true;
                break;
            }
        }
        assertTrue(different);
    }

    @Test
    void zipEocdIsDistinctFromZipEocd64() {
        boolean different = false;
        for (int i = 0; i < CrxConstants.ZIP_EOCD.length; i++) {
            if (CrxConstants.ZIP_EOCD[i] != CrxConstants.ZIP_EOCD64[i]) {
                different = true;
                break;
            }
        }
        assertTrue(different);
    }

    @Test
    void constantArraysAreNotEmpty() {
        assertTrue(CrxConstants.MAGIC_FULL.length > 0);
        assertTrue(CrxConstants.MAGIC_DIFF.length > 0);
        assertTrue(CrxConstants.SIGNATURE_CONTEXT.length > 0);
        assertTrue(CrxConstants.ZIP_EOCD.length > 0);
        assertTrue(CrxConstants.ZIP_EOCD64.length > 0);
    }
}
