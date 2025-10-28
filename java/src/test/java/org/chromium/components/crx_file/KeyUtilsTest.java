package org.chromium.components.crx_file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

class KeyUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void generateRsaKeyPair2048() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
        
        assertNotNull(keyPair);
        assertNotNull(keyPair.getPrivate());
        assertNotNull(keyPair.getPublic());
        assertTrue(keyPair.getPrivate() instanceof RSAPrivateKey);
        assertTrue(keyPair.getPublic() instanceof RSAPublicKey);
        
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        assertEquals(2048, publicKey.getModulus().bitLength());
    }

    @Test
    void generateRsaKeyPair3072() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(3072);
        
        assertNotNull(keyPair);
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        assertEquals(3072, publicKey.getModulus().bitLength());
    }

    @Test
    void generateRsaKeyPair4096() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(4096);
        
        assertNotNull(keyPair);
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        assertEquals(4096, publicKey.getModulus().bitLength());
    }

    @Test
    void generateRsaKeyPairProducesDifferentKeys() throws Exception {
        KeyPair keyPair1 = KeyUtils.generateRsaKeyPair(2048);
        KeyPair keyPair2 = KeyUtils.generateRsaKeyPair(2048);
        
        assertNotNull(keyPair1);
        assertNotNull(keyPair2);
        
        RSAPrivateKey privateKey1 = (RSAPrivateKey) keyPair1.getPrivate();
        RSAPrivateKey privateKey2 = (RSAPrivateKey) keyPair2.getPrivate();
        
        assertTrue(!privateKey1.getModulus().equals(privateKey2.getModulus()));
    }

    @Test
    void writePkcs8PrivateKey() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
        Path keyFile = tempDir.resolve("test.pem");
        
        KeyUtils.writePkcs8PrivateKey(keyFile, keyPair.getPrivate());
        
        assertTrue(Files.exists(keyFile));
        String content = Files.readString(keyFile);
        assertTrue(content.contains("-----BEGIN PRIVATE KEY-----"));
        assertTrue(content.contains("-----END PRIVATE KEY-----"));
    }

    @Test
    void writePkcs8PrivateKeyCreatesParentDirectories() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
        Path keyFile = tempDir.resolve("subdir1/subdir2/test.pem");
        
        KeyUtils.writePkcs8PrivateKey(keyFile, keyPair.getPrivate());
        
        assertTrue(Files.exists(keyFile));
    }

    @Test
    void readPkcs8PrivateKey() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
        Path keyFile = tempDir.resolve("test.pem");
        
        KeyUtils.writePkcs8PrivateKey(keyFile, keyPair.getPrivate());
        PrivateKey readKey = KeyUtils.readPkcs8PrivateKey(keyFile);
        
        assertNotNull(readKey);
        assertTrue(readKey instanceof RSAPrivateKey);
        
        RSAPrivateKey originalKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPrivateKey restoredKey = (RSAPrivateKey) readKey;
        
        assertEquals(originalKey.getModulus(), restoredKey.getModulus());
        assertEquals(originalKey.getPrivateExponent(), restoredKey.getPrivateExponent());
    }

    @Test
    void readPkcs8PrivateKeyWithExtraWhitespace() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
        Path keyFile = tempDir.resolve("test.pem");
        
        KeyUtils.writePkcs8PrivateKey(keyFile, keyPair.getPrivate());
        
        String content = Files.readString(keyFile);
        String withExtraWhitespace = content.replaceAll("\n", "\n\n");
        Files.writeString(keyFile, withExtraWhitespace);
        
        PrivateKey readKey = KeyUtils.readPkcs8PrivateKey(keyFile);
        
        assertNotNull(readKey);
        assertTrue(readKey instanceof RSAPrivateKey);
    }

    @Test
    void readPkcs8PrivateKeyNonExistentFile() {
        Path keyFile = tempDir.resolve("nonexistent.pem");
        
        IOException exception = assertThrows(
            IOException.class,
            () -> KeyUtils.readPkcs8PrivateKey(keyFile)
        );
        
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    @Test
    void readPkcs8PrivateKeyInvalidFormat() throws Exception {
        Path keyFile = tempDir.resolve("invalid.pem");
        Files.writeString(keyFile, "This is not a valid PEM file");
        
        GeneralSecurityException exception = assertThrows(
            GeneralSecurityException.class,
            () -> KeyUtils.readPkcs8PrivateKey(keyFile)
        );
        
        assertTrue(exception.getMessage().contains("Unsupported private key format"));
    }

    @Test
    void readPkcs8PrivateKeyMissingBeginMarker() throws Exception {
        Path keyFile = tempDir.resolve("invalid.pem");
        String content = "Some content\n-----END PRIVATE KEY-----";
        Files.writeString(keyFile, content);
        
        GeneralSecurityException exception = assertThrows(
            GeneralSecurityException.class,
            () -> KeyUtils.readPkcs8PrivateKey(keyFile)
        );
        
        assertTrue(exception.getMessage().contains("Unsupported private key format"));
    }

    @Test
    void readPkcs8PrivateKeyMissingEndMarker() throws Exception {
        Path keyFile = tempDir.resolve("invalid.pem");
        String content = "-----BEGIN PRIVATE KEY-----\nSome content";
        Files.writeString(keyFile, content);
        
        GeneralSecurityException exception = assertThrows(
            GeneralSecurityException.class,
            () -> KeyUtils.readPkcs8PrivateKey(keyFile)
        );
        
        assertTrue(exception.getMessage().contains("Unsupported private key format"));
    }

    @Test
    void readPkcs8PrivateKeyRoundTrip() throws Exception {
        int[] keySizes = {2048, 3072, 4096};
        
        for (int keySize : keySizes) {
            KeyPair keyPair = KeyUtils.generateRsaKeyPair(keySize);
            Path keyFile = tempDir.resolve("test_" + keySize + ".pem");
            
            KeyUtils.writePkcs8PrivateKey(keyFile, keyPair.getPrivate());
            PrivateKey readKey = KeyUtils.readPkcs8PrivateKey(keyFile);
            
            RSAPrivateKey originalKey = (RSAPrivateKey) keyPair.getPrivate();
            RSAPrivateKey restoredKey = (RSAPrivateKey) readKey;
            
            assertEquals(originalKey.getModulus(), restoredKey.getModulus());
            assertEquals(originalKey.getPrivateExponent(), restoredKey.getPrivateExponent());
        }
    }

    @Test
    void writePkcs8PrivateKeyFormatIsCorrect() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
        Path keyFile = tempDir.resolve("test.pem");
        
        KeyUtils.writePkcs8PrivateKey(keyFile, keyPair.getPrivate());
        
        String content = Files.readString(keyFile);
        
        assertTrue(content.startsWith("-----BEGIN PRIVATE KEY-----\n"));
        assertTrue(content.endsWith("-----END PRIVATE KEY-----\n"));
        
        String[] lines = content.split("\n");
        assertTrue(lines.length > 3);
        
        for (int i = 1; i < lines.length - 1; i++) {
            if (!lines[i].equals("-----END PRIVATE KEY-----")) {
                assertTrue(lines[i].length() <= 64, "Line " + i + " is too long: " + lines[i].length());
            }
        }
    }

    @Test
    void readPkcs8PrivateKeyFromManuallyCreatedPem() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
        Path keyFile = tempDir.resolve("manual.pem");
        
        byte[] encoded = keyPair.getPrivate().getEncoded();
        String base64 = java.util.Base64.getEncoder().encodeToString(encoded);
        
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            int end = Math.min(i + 64, base64.length());
            pem.append(base64, i, end).append("\n");
        }
        pem.append("-----END PRIVATE KEY-----\n");
        
        Files.writeString(keyFile, pem.toString());
        
        PrivateKey readKey = KeyUtils.readPkcs8PrivateKey(keyFile);
        
        assertNotNull(readKey);
        RSAPrivateKey originalKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAPrivateKey restoredKey = (RSAPrivateKey) readKey;
        
        assertEquals(originalKey.getModulus(), restoredKey.getModulus());
    }

    @Test
    void readPkcs8PrivateKeyWithLeadingAndTrailingWhitespace() throws Exception {
        KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
        Path keyFile = tempDir.resolve("test.pem");
        
        KeyUtils.writePkcs8PrivateKey(keyFile, keyPair.getPrivate());
        String content = Files.readString(keyFile);
        
        Files.writeString(keyFile, "\n\n" + content + "\n\n");
        
        PrivateKey readKey = KeyUtils.readPkcs8PrivateKey(keyFile);
        
        assertNotNull(readKey);
        assertTrue(readKey instanceof RSAPrivateKey);
    }

    @Test
    void multipleKeyRoundTrips() throws Exception {
        for (int i = 0; i < 5; i++) {
            KeyPair keyPair = KeyUtils.generateRsaKeyPair(2048);
            Path keyFile = tempDir.resolve("test_" + i + ".pem");
            
            KeyUtils.writePkcs8PrivateKey(keyFile, keyPair.getPrivate());
            PrivateKey readKey = KeyUtils.readPkcs8PrivateKey(keyFile);
            
            Path keyFile2 = tempDir.resolve("test_" + i + "_copy.pem");
            KeyUtils.writePkcs8PrivateKey(keyFile2, readKey);
            PrivateKey readKey2 = KeyUtils.readPkcs8PrivateKey(keyFile2);
            
            RSAPrivateKey originalKey = (RSAPrivateKey) keyPair.getPrivate();
            RSAPrivateKey finalKey = (RSAPrivateKey) readKey2;
            
            assertEquals(originalKey.getModulus(), finalKey.getModulus());
            assertEquals(originalKey.getPrivateExponent(), finalKey.getPrivateExponent());
        }
    }
}
