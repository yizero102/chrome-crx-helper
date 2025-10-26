package org.chromium.components.crx_file;

import com.google.protobuf.ByteString;

import crx_file.Crx3;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;

public final class CrxCreator {
    private static final int BUFFER_SIZE = 1 << 12;

    private CrxCreator() {}

    public static CreatorResult create(Path outputPath,
                                       Path zipPath,
                                       PrivateKey signingKey) {
        return createInternal(outputPath, zipPath, signingKey, null);
    }

    public static CreatorResult createWithVerifiedContents(Path outputPath,
                                                            Path zipPath,
                                                            PrivateKey signingKey,
                                                            byte[] verifiedContents) {
        return createInternal(outputPath, zipPath, signingKey, verifiedContents);
    }

    private static CreatorResult createInternal(Path outputPath,
                                                Path zipPath,
                                                PrivateKey signingKey,
                                                byte[] verifiedContents) {
        if (outputPath == null || zipPath == null) {
            throw new IllegalArgumentException("Paths must not be null");
        }
        if (signingKey == null) {
            throw new IllegalArgumentException("signingKey must not be null");
        }
        if (!Files.isReadable(zipPath)) {
            return CreatorResult.ERROR_FILE_NOT_READABLE;
        }

        byte[] publicKeyBytes;
        byte[] signedHeaderBytes;
        byte[] signedHeaderSize;
        try {
            publicKeyBytes = derivePublicKey(signingKey);
            signedHeaderBytes = buildSignedHeaderData(publicKeyBytes);
            signedHeaderSize = ByteUtils.toLittleEndian(signedHeaderBytes.length);
        } catch (GeneralSecurityException e) {
            return CreatorResult.ERROR_SIGNING_FAILURE;
        }

        Signature signer;
        try {
            signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(signingKey);
            signer.update(CrxConstants.SIGNATURE_CONTEXT);
            signer.update(signedHeaderSize);
            signer.update(signedHeaderBytes);
        } catch (GeneralSecurityException e) {
            return CreatorResult.ERROR_SIGNING_FAILURE;
        }

        try (InputStream in = Files.newInputStream(zipPath)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                signer.update(buffer, 0, read);
            }
        } catch (IOException e) {
            return CreatorResult.ERROR_FILE_NOT_READABLE;
        } catch (GeneralSecurityException e) {
            return CreatorResult.ERROR_SIGNING_FAILURE;
        }

        byte[] signatureBytes;
        try {
            signatureBytes = signer.sign();
        } catch (GeneralSecurityException e) {
            return CreatorResult.ERROR_SIGNING_FAILURE;
        }

        Crx3.CrxFileHeader.Builder headerBuilder = Crx3.CrxFileHeader.newBuilder()
                .setSignedHeaderData(ByteString.copyFrom(signedHeaderBytes))
                .addSha256WithRsa(Crx3.AsymmetricKeyProof.newBuilder()
                        .setPublicKey(ByteString.copyFrom(publicKeyBytes))
                        .setSignature(ByteString.copyFrom(signatureBytes))
                        .build());
        if (verifiedContents != null && verifiedContents.length > 0) {
            headerBuilder.setVerifiedContents(ByteString.copyFrom(verifiedContents));
        }
        byte[] headerBytes = headerBuilder.build().toByteArray();
        byte[] headerSize = ByteUtils.toLittleEndian(headerBytes.length);

        OutputStream outputStream;
        try {
            outputStream = Files.newOutputStream(outputPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            return CreatorResult.ERROR_FILE_NOT_WRITABLE;
        }

        try (OutputStream out = new BufferedOutputStream(outputStream)) {
            out.write(CrxConstants.MAGIC_FULL);
            ByteUtils.writeLittleEndian(out, CrxConstants.CRX3_VERSION);
            out.write(headerSize);
            out.write(headerBytes);
            try (InputStream in = Files.newInputStream(zipPath)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
            out.flush();
        } catch (IOException e) {
            return CreatorResult.ERROR_FILE_WRITE_FAILURE;
        }

        return CreatorResult.OK;
    }

    private static byte[] derivePublicKey(PrivateKey signingKey) throws GeneralSecurityException {
        if (signingKey instanceof RSAPrivateCrtKey rsaPrivate) {
            RSAPublicKeySpec spec = new RSAPublicKeySpec(rsaPrivate.getModulus(), rsaPrivate.getPublicExponent());
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(spec).getEncoded();
        }
        throw new GeneralSecurityException("Only RSA private keys are supported for CRX creation");
    }

    private static byte[] buildSignedHeaderData(byte[] publicKeyBytes) throws GeneralSecurityException {
        byte[] truncatedHash = Arrays.copyOf(hash(publicKeyBytes), IdUtil.ID_SIZE);
        Crx3.SignedData signedData = Crx3.SignedData.newBuilder()
                .setCrxId(ByteString.copyFrom(truncatedHash))
                .build();
        return signedData.toByteArray();
    }

    private static byte[] hash(byte[] input) throws GeneralSecurityException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new GeneralSecurityException("SHA-256 not available", e);
        }
    }
}
