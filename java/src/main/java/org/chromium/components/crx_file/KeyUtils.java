package org.chromium.components.crx_file;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

final class KeyUtils {
    private static final String BEGIN_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----";
    private static final String END_PRIVATE_KEY = "-----END PRIVATE KEY-----";

    private KeyUtils() {}

    static KeyPair generateRsaKeyPair(int keySize) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(keySize);
        return generator.generateKeyPair();
    }

    static PrivateKey readPkcs8PrivateKey(Path path) throws IOException, GeneralSecurityException {
        if (!Files.exists(path)) {
            throw new IOException("Private key file does not exist: " + path);
        }
        String pem = Files.readString(path, StandardCharsets.US_ASCII);
        int begin = pem.indexOf(BEGIN_PRIVATE_KEY);
        int end = pem.indexOf(END_PRIVATE_KEY);
        if (begin < 0 || end < 0) {
            throw new GeneralSecurityException("Unsupported private key format. Expected PKCS#8 with 'BEGIN PRIVATE KEY'.");
        }
        String base64 = pem.substring(begin + BEGIN_PRIVATE_KEY.length(), end)
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return decodePkcs8(der);
    }

    static void writePkcs8PrivateKey(Path path, PrivateKey privateKey) throws IOException {
        byte[] encoded = privateKey.getEncoded();
        String base64 = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded);
        String pem = BEGIN_PRIVATE_KEY + "\n" + base64 + "\n" + END_PRIVATE_KEY + "\n";
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(path, pem, StandardCharsets.US_ASCII);
    }

    private static PrivateKey decodePkcs8(byte[] der) throws GeneralSecurityException {
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (InvalidKeySpecException e) {
            throw new GeneralSecurityException("Invalid PKCS#8 RSA private key", e);
        }
    }
}
