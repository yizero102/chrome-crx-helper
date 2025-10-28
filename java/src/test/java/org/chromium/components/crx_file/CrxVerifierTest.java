package org.chromium.components.crx_file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class CrxVerifierTest {

    @TempDir
    Path tempDir;

    @Test
    void verifyWithNullPathThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> CrxVerifier.verify(null, VerifierFormat.CRX3, List.of(), new byte[0]));
    }

    @Test
    void verifyWithNullFormatThrows() throws Exception {
        Path crxPath = createValidCrx();
        
        assertThrows(IllegalArgumentException.class,
            () -> CrxVerifier.verify(crxPath, null, List.of(), new byte[0]));
    }

    @Test
    void verifyNonExistentFile() {
        Path crxPath = tempDir.resolve("nonexistent.crx");
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.ERROR_FILE_NOT_READABLE, verification.getResult());
    }

    @Test
    void verifyValidCrx() throws Exception {
        Path crxPath = createValidCrx();
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
        assertFalse(verification.isDelta());
        assertTrue(verification.getPublicKeyBase64().isPresent());
        assertTrue(verification.getCrxId().isPresent());
    }

    @Test
    void verifyReturnsCorrectPublicKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path crxPath = createValidCrxWithKey(keyPair);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
        assertTrue(verification.getPublicKeyBase64().isPresent());
        
        String expectedPublicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        assertEquals(expectedPublicKey, verification.getPublicKeyBase64().get());
    }

    @Test
    void verifyReturnsCorrectCrxId() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path crxPath = createValidCrxWithKey(keyPair);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
        assertTrue(verification.getCrxId().isPresent());
        
        String expectedCrxId = IdUtil.generateId(keyPair.getPublic().getEncoded());
        assertEquals(expectedCrxId, verification.getCrxId().get());
        assertTrue(IdUtil.isValid(verification.getCrxId().get()));
    }

    @Test
    void verifyWithInvalidMagic() throws Exception {
        Path crxPath = tempDir.resolve("invalid.crx");
        Files.writeString(crxPath, "INVALID CRX FILE");
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.ERROR_HEADER_INVALID, verification.getResult());
    }

    @Test
    void verifyWithTruncatedFile() throws Exception {
        Path validCrx = createValidCrx();
        byte[] content = Files.readAllBytes(validCrx);
        
        Path truncatedCrx = tempDir.resolve("truncated.crx");
        Files.write(truncatedCrx, java.util.Arrays.copyOf(content, 10));
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            truncatedCrx, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.ERROR_HEADER_INVALID, verification.getResult());
    }

    @Test
    void verifyWithEmptyFile() throws Exception {
        Path emptyCrx = tempDir.resolve("empty.crx");
        Files.createFile(emptyCrx);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            emptyCrx, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.ERROR_HEADER_INVALID, verification.getResult());
    }

    @Test
    void verifyWithCorrectFileHash() throws Exception {
        Path crxPath = createValidCrx();
        byte[] crxContent = Files.readAllBytes(crxPath);
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] expectedHash = digest.digest(crxContent);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), expectedHash);
        
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
    }

    @Test
    void verifyWithIncorrectFileHash() throws Exception {
        Path crxPath = createValidCrx();
        byte[] wrongHash = new byte[32];
        java.util.Arrays.fill(wrongHash, (byte) 0xFF);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), wrongHash);
        
        assertEquals(VerifierResult.ERROR_FILE_HASH_FAILED, verification.getResult());
    }

    @Test
    void verifyWithInvalidFileHashLength() throws Exception {
        Path crxPath = createValidCrx();
        byte[] invalidHash = new byte[16];
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), invalidHash);
        
        assertEquals(VerifierResult.ERROR_EXPECTED_HASH_INVALID, verification.getResult());
    }

    @Test
    void verifyWithEmptyFileHash() throws Exception {
        Path crxPath = createValidCrx();
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
    }

    @Test
    void verifyWithRequiredKeyHash() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path crxPath = createValidCrxWithKey(keyPair);
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyHash = digest.digest(keyPair.getPublic().getEncoded());
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(keyHash), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
    }

    @Test
    void verifyWithMissingRequiredKeyHash() throws Exception {
        Path crxPath = createValidCrx();
        byte[] wrongKeyHash = new byte[32];
        java.util.Arrays.fill(wrongKeyHash, (byte) 0xFF);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(wrongKeyHash), new byte[0]);
        
        assertEquals(VerifierResult.ERROR_REQUIRED_PROOF_MISSING, verification.getResult());
    }

    @Test
    void verifyWithMultipleRequiredKeyHashes() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path crxPath = createValidCrxWithKey(keyPair);
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] correctKeyHash = digest.digest(keyPair.getPublic().getEncoded());
        byte[] wrongKeyHash = new byte[32];
        java.util.Arrays.fill(wrongKeyHash, (byte) 0xAA);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(correctKeyHash, wrongKeyHash), new byte[0]);
        
        assertEquals(VerifierResult.ERROR_REQUIRED_PROOF_MISSING, verification.getResult());
    }

    @Test
    void verifyWithVerifiedContents() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        Path crxPath = tempDir.resolve("output.crx");
        
        byte[] verifiedContents = "verified contents".getBytes();
        CreatorResult createResult = CrxCreator.createWithVerifiedContents(
            crxPath, zipPath, keyPair.getPrivate(), verifiedContents);
        assertEquals(CreatorResult.OK, createResult);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
        assertTrue(verification.getCompressedVerifiedContents().isPresent());
        assertArrayEquals(verifiedContents, verification.getCompressedVerifiedContents().get());
    }

    @Test
    void verifyWithoutVerifiedContents() throws Exception {
        Path crxPath = createValidCrx();
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
        assertFalse(verification.getCompressedVerifiedContents().isPresent());
    }

    @Test
    void verifyDeltaCrxFormat() throws Exception {
        Path crxPath = createValidCrx();
        byte[] content = Files.readAllBytes(crxPath);
        
        content[0] = 'C';
        content[1] = 'r';
        content[2] = 'O';
        content[3] = 'D';
        
        Path deltaCrx = tempDir.resolve("delta.crx");
        Files.write(deltaCrx, content);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            deltaCrx, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_DELTA, verification.getResult());
        assertTrue(verification.isDelta());
    }

    @Test
    void verifyReturnsEmptyPublicKeyOnFailure() {
        Path crxPath = tempDir.resolve("nonexistent.crx");
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertFalse(verification.getPublicKeyBase64().isPresent());
    }

    @Test
    void verifyReturnsEmptyCrxIdOnFailure() {
        Path crxPath = tempDir.resolve("nonexistent.crx");
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            crxPath, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertFalse(verification.getCrxId().isPresent());
    }

    @Test
    void verifyWithCorruptedSignature() throws Exception {
        Path crxPath = createValidCrx();
        byte[] content = Files.readAllBytes(crxPath);
        
        int signatureLocation = content.length / 2;
        content[signatureLocation] ^= 0xFF;
        
        Path corruptedCrx = tempDir.resolve("corrupted.crx");
        Files.write(corruptedCrx, content);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            corruptedCrx, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertTrue(
            verification.getResult() == VerifierResult.ERROR_SIGNATURE_VERIFICATION_FAILED ||
            verification.getResult() == VerifierResult.ERROR_HEADER_INVALID
        );
    }

    @Test
    void verifyDifferentFormats() throws Exception {
        Path crxPath = createValidCrx();
        
        VerifierFormat[] formats = {
            VerifierFormat.CRX3,
            VerifierFormat.CRX3_WITH_TEST_PUBLISHER_PROOF,
            VerifierFormat.CRX3_WITH_PUBLISHER_PROOF
        };
        
        for (VerifierFormat format : formats) {
            CrxVerifier.Verification verification = CrxVerifier.verify(
                crxPath, format, List.of(), new byte[0]);
            
            assertTrue(
                verification.getResult() == VerifierResult.OK_FULL ||
                verification.getResult() == VerifierResult.ERROR_REQUIRED_PROOF_MISSING,
                "Unexpected result for format: " + format
            );
        }
    }

    @Test
    void verifyMultipleCrxWithSameKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        
        Path crx1 = createValidCrxWithKey(keyPair);
        Path crx2 = createValidCrxWithKey(keyPair);
        
        CrxVerifier.Verification verification1 = CrxVerifier.verify(
            crx1, VerifierFormat.CRX3, List.of(), new byte[0]);
        CrxVerifier.Verification verification2 = CrxVerifier.verify(
            crx2, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification1.getResult());
        assertEquals(VerifierResult.OK_FULL, verification2.getResult());
        assertEquals(verification1.getCrxId().get(), verification2.getCrxId().get());
    }

    @Test
    void verifyMultipleCrxWithDifferentKeys() throws Exception {
        KeyPair keyPair1 = generateRsaKeyPair();
        KeyPair keyPair2 = generateRsaKeyPair();
        
        Path crx1 = createValidCrxWithKey(keyPair1);
        Path crx2 = createValidCrxWithKey(keyPair2);
        
        CrxVerifier.Verification verification1 = CrxVerifier.verify(
            crx1, VerifierFormat.CRX3, List.of(), new byte[0]);
        CrxVerifier.Verification verification2 = CrxVerifier.verify(
            crx2, VerifierFormat.CRX3, List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification1.getResult());
        assertEquals(VerifierResult.OK_FULL, verification2.getResult());
        assertFalse(verification1.getCrxId().get().equals(verification2.getCrxId().get()));
    }

    private KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private Path createValidCrx() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        return createValidCrxWithKey(keyPair);
    }

    private Path createValidCrxWithKey(KeyPair keyPair) throws Exception {
        Path zipPath = createSimpleZip();
        Path crxPath = tempDir.resolve("test_" + System.nanoTime() + ".crx");
        
        CreatorResult result = CrxCreator.create(crxPath, zipPath, keyPair.getPrivate());
        if (result != CreatorResult.OK) {
            throw new RuntimeException("Failed to create CRX: " + result);
        }
        
        return crxPath;
    }

    private Path createSimpleZip() throws IOException {
        Path zipPath = tempDir.resolve("test_" + System.nanoTime() + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            ZipEntry entry = new ZipEntry("manifest.json");
            zos.putNextEntry(entry);
            zos.write("{\"name\":\"Test Extension\"}".getBytes());
            zos.closeEntry();
        }
        return zipPath;
    }
}
