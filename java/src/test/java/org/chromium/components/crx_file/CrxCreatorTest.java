package org.chromium.components.crx_file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class CrxCreatorTest {

    @TempDir
    Path tempDir;

    @Test
    void createWithNullOutputPathThrows() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        
        assertThrows(IllegalArgumentException.class,
            () -> CrxCreator.create(null, zipPath, keyPair.getPrivate()));
    }

    @Test
    void createWithNullZipPathThrows() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path outputPath = tempDir.resolve("output.crx");
        
        assertThrows(IllegalArgumentException.class,
            () -> CrxCreator.create(outputPath, null, keyPair.getPrivate()));
    }

    @Test
    void createWithNullSigningKeyThrows() throws Exception {
        Path zipPath = createSimpleZip();
        Path outputPath = tempDir.resolve("output.crx");
        
        assertThrows(IllegalArgumentException.class,
            () -> CrxCreator.create(outputPath, zipPath, null));
    }

    @Test
    void createWithNonExistentZipFile() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = tempDir.resolve("nonexistent.zip");
        Path outputPath = tempDir.resolve("output.crx");
        
        CreatorResult result = CrxCreator.create(outputPath, zipPath, keyPair.getPrivate());
        
        assertEquals(CreatorResult.ERROR_FILE_NOT_READABLE, result);
    }

    @Test
    void createSuccessfully() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        Path outputPath = tempDir.resolve("output.crx");
        
        CreatorResult result = CrxCreator.create(outputPath, zipPath, keyPair.getPrivate());
        
        assertEquals(CreatorResult.OK, result);
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > 0);
    }

    @Test
    void createProducesDifferentOutputForDifferentKeys() throws Exception {
        Path zipPath = createSimpleZip();
        
        KeyPair keyPair1 = generateRsaKeyPair();
        Path outputPath1 = tempDir.resolve("output1.crx");
        CreatorResult result1 = CrxCreator.create(outputPath1, zipPath, keyPair1.getPrivate());
        assertEquals(CreatorResult.OK, result1);
        
        KeyPair keyPair2 = generateRsaKeyPair();
        Path outputPath2 = tempDir.resolve("output2.crx");
        CreatorResult result2 = CrxCreator.create(outputPath2, zipPath, keyPair2.getPrivate());
        assertEquals(CreatorResult.OK, result2);
        
        byte[] crx1 = Files.readAllBytes(outputPath1);
        byte[] crx2 = Files.readAllBytes(outputPath2);
        
        assertFalse(java.util.Arrays.equals(crx1, crx2));
    }

    @Test
    void createWithEmptyZip() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = tempDir.resolve("empty.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
        }
        
        Path outputPath = tempDir.resolve("output.crx");
        CreatorResult result = CrxCreator.create(outputPath, zipPath, keyPair.getPrivate());
        
        assertEquals(CreatorResult.OK, result);
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void createWithLargeZip() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = tempDir.resolve("large.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (int i = 0; i < 100; i++) {
                ZipEntry entry = new ZipEntry("file" + i + ".txt");
                zos.putNextEntry(entry);
                byte[] content = ("Content of file " + i).repeat(100).getBytes();
                zos.write(content);
                zos.closeEntry();
            }
        }
        
        Path outputPath = tempDir.resolve("output.crx");
        CreatorResult result = CrxCreator.create(outputPath, zipPath, keyPair.getPrivate());
        
        assertEquals(CreatorResult.OK, result);
        assertTrue(Files.exists(outputPath));
        assertTrue(Files.size(outputPath) > Files.size(zipPath));
    }

    @Test
    void createOverwritesExistingFile() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        Path outputPath = tempDir.resolve("output.crx");
        
        Files.writeString(outputPath, "existing content");
        
        CreatorResult result = CrxCreator.create(outputPath, zipPath, keyPair.getPrivate());
        
        assertEquals(CreatorResult.OK, result);
        assertTrue(Files.exists(outputPath));
        
        byte[] content = Files.readAllBytes(outputPath);
        assertTrue(content.length > "existing content".length());
        assertTrue(content[0] == 'C' && content[1] == 'r' && content[2] == '2' && content[3] == '4');
    }

    @Test
    void createWithVerifiedContentsNull() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        Path outputPath = tempDir.resolve("output.crx");
        
        CreatorResult result = CrxCreator.createWithVerifiedContents(
            outputPath, zipPath, keyPair.getPrivate(), null);
        
        assertEquals(CreatorResult.OK, result);
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void createWithVerifiedContentsEmpty() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        Path outputPath = tempDir.resolve("output.crx");
        
        CreatorResult result = CrxCreator.createWithVerifiedContents(
            outputPath, zipPath, keyPair.getPrivate(), new byte[0]);
        
        assertEquals(CreatorResult.OK, result);
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void createWithVerifiedContentsNonEmpty() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        Path outputPath = tempDir.resolve("output.crx");
        byte[] verifiedContents = "verified contents data".getBytes();
        
        CreatorResult result = CrxCreator.createWithVerifiedContents(
            outputPath, zipPath, keyPair.getPrivate(), verifiedContents);
        
        assertEquals(CreatorResult.OK, result);
        assertTrue(Files.exists(outputPath));
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            outputPath, VerifierFormat.CRX3, java.util.List.of(), new byte[0]);
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
        assertTrue(verification.getCompressedVerifiedContents().isPresent());
    }

    @Test
    void createProducesValidCrxMagic() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        Path outputPath = tempDir.resolve("output.crx");
        
        CreatorResult result = CrxCreator.create(outputPath, zipPath, keyPair.getPrivate());
        
        assertEquals(CreatorResult.OK, result);
        
        byte[] content = Files.readAllBytes(outputPath);
        assertTrue(content.length > 4);
        assertEquals('C', content[0]);
        assertEquals('r', content[1]);
        assertEquals('2', content[2]);
        assertEquals('4', content[3]);
    }

    @Test
    void createWithDifferentKeySizes() throws Exception {
        int[] keySizes = {2048, 3072, 4096};
        Path zipPath = createSimpleZip();
        
        for (int keySize : keySizes) {
            KeyPair keyPair = generateRsaKeyPairWithSize(keySize);
            Path outputPath = tempDir.resolve("output_" + keySize + ".crx");
            
            CreatorResult result = CrxCreator.create(outputPath, zipPath, keyPair.getPrivate());
            
            assertEquals(CreatorResult.OK, result, "Failed for key size: " + keySize);
            assertTrue(Files.exists(outputPath));
        }
    }

    @Test
    void createIsDeterministicWithSameKey() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        
        Path outputPath1 = tempDir.resolve("output1.crx");
        CreatorResult result1 = CrxCreator.create(outputPath1, zipPath, keyPair.getPrivate());
        assertEquals(CreatorResult.OK, result1);
        
        Path outputPath2 = tempDir.resolve("output2.crx");
        CreatorResult result2 = CrxCreator.create(outputPath2, zipPath, keyPair.getPrivate());
        assertEquals(CreatorResult.OK, result2);
        
        byte[] crx1 = Files.readAllBytes(outputPath1);
        byte[] crx2 = Files.readAllBytes(outputPath2);
        
        assertEquals(crx1.length, crx2.length);
    }

    @Test
    void createProducesVerifiableCrx() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        Path zipPath = createSimpleZip();
        Path outputPath = tempDir.resolve("output.crx");
        
        CreatorResult createResult = CrxCreator.create(outputPath, zipPath, keyPair.getPrivate());
        assertEquals(CreatorResult.OK, createResult);
        
        CrxVerifier.Verification verification = CrxVerifier.verify(
            outputPath, VerifierFormat.CRX3, java.util.List.of(), new byte[0]);
        
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
    }

    private KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        return generateRsaKeyPairWithSize(2048);
    }

    private KeyPair generateRsaKeyPairWithSize(int keySize) throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize);
        return generator.generateKeyPair();
    }

    private Path createSimpleZip() throws IOException {
        Path zipPath = tempDir.resolve("test.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            ZipEntry entry1 = new ZipEntry("file1.txt");
            zos.putNextEntry(entry1);
            zos.write("content1".getBytes());
            zos.closeEntry();
            
            ZipEntry entry2 = new ZipEntry("file2.txt");
            zos.putNextEntry(entry2);
            zos.write("content2".getBytes());
            zos.closeEntry();
        }
        return zipPath;
    }
}
