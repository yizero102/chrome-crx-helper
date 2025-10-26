package org.chromium.components.crx_file;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import crx_file.Crx3;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CrxVerifier {
    private static final int BUFFER_SIZE = 1 << 12;
    private static final byte[] PUBLISHER_KEY_HASH = new byte[] {
            (byte) 0x61, (byte) 0xf7, (byte) 0xf2, (byte) 0xa6,
            (byte) 0xbf, (byte) 0xcf, (byte) 0x74, (byte) 0xcd,
            (byte) 0x0b, (byte) 0xc1, (byte) 0xfe, (byte) 0x24,
            (byte) 0x97, (byte) 0xcc, (byte) 0x9b, (byte) 0x04,
            (byte) 0x25, (byte) 0x4c, (byte) 0x65, (byte) 0x8f,
            (byte) 0x79, (byte) 0xf2, (byte) 0x14, (byte) 0x53,
            (byte) 0x92, (byte) 0x86, (byte) 0x7e, (byte) 0xa8,
            (byte) 0x36, (byte) 0x63, (byte) 0x67, (byte) 0xcf
    };
    private static final byte[] PUBLISHER_TEST_KEY_HASH = new byte[] {
            (byte) 0x6c, (byte) 0x46, (byte) 0x41, (byte) 0x3b,
            (byte) 0x00, (byte) 0xd0, (byte) 0xfa, (byte) 0x0e,
            (byte) 0x72, (byte) 0xc8, (byte) 0xd2, (byte) 0x5f,
            (byte) 0x64, (byte) 0xf3, (byte) 0xa6, (byte) 0x17,
            (byte) 0x03, (byte) 0x0d, (byte) 0xde, (byte) 0x21,
            (byte) 0x61, (byte) 0xbe, (byte) 0xb7, (byte) 0x95,
            (byte) 0x91, (byte) 0x95, (byte) 0x83, (byte) 0x68,
            (byte) 0x12, (byte) 0xe9, (byte) 0x78, (byte) 0x1e
    };

    private CrxVerifier() {}

    public static Verification verify(Path crxPath,
                                      VerifierFormat format,
                                      List<byte[]> requiredKeyHashes,
                                      byte[] requiredFileHash) {
        if (crxPath == null) {
            throw new IllegalArgumentException("crxPath must not be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("format must not be null");
        }

        InputStream rawInput;
        try {
            rawInput = Files.newInputStream(crxPath);
        } catch (IOException e) {
            return Verification.failure(VerifierResult.ERROR_FILE_NOT_READABLE);
        }

        try (InputStream input = new BufferedInputStream(rawInput)) {
            MessageDigest fileHash = MessageDigest.getInstance("SHA-256");
            byte[] magic = readExactly(input, CrxConstants.MAGIC_FULL.length, fileHash);
            boolean diff;
            if (Arrays.equals(magic, CrxConstants.MAGIC_DIFF)) {
                diff = true;
            } else if (Arrays.equals(magic, CrxConstants.MAGIC_FULL)) {
                diff = false;
            } else {
                return Verification.failure(VerifierResult.ERROR_HEADER_INVALID);
            }

            int version = readLittleEndianInt(input, fileHash);
            if (version != CrxConstants.CRX3_VERSION) {
                return Verification.failure(VerifierResult.ERROR_HEADER_INVALID);
            }

            int headerSize = readLittleEndianInt(input, fileHash);
            if (headerSize < 0) {
                return Verification.failure(VerifierResult.ERROR_HEADER_INVALID);
            }
            byte[] headerBytes = readExactly(input, headerSize, fileHash);
            if (ByteUtils.containsSubsequence(headerBytes, CrxConstants.ZIP_EOCD)
                    || ByteUtils.containsSubsequence(headerBytes, CrxConstants.ZIP_EOCD64)) {
                return Verification.failure(VerifierResult.ERROR_HEADER_INVALID);
            }

            Crx3.CrxFileHeader header = parseHeader(headerBytes);
            if (!header.hasSignedHeaderData()) {
                return Verification.failure(VerifierResult.ERROR_HEADER_INVALID);
            }

            ByteString signedHeaderDataBytes = header.getSignedHeaderData();
            byte[] signedHeaderDataArray = signedHeaderDataBytes.toByteArray();
            Crx3.SignedData signedData = parseSignedData(signedHeaderDataBytes);
            byte[] crxIdBinary = signedData.getCrxId().toByteArray();
            if (crxIdBinary.length != IdUtil.ID_SIZE) {
                return Verification.failure(VerifierResult.ERROR_HEADER_INVALID);
            }
            String declaredCrxId = IdUtil.generateIdFromHex(ByteUtils.toHex(crxIdBinary));
            byte[] signedHeaderSize = ByteUtils.toLittleEndian(signedHeaderDataArray.length);

            boolean requirePublisherKey =
                    format == VerifierFormat.CRX3_WITH_PUBLISHER_PROOF
                            || format == VerifierFormat.CRX3_WITH_TEST_PUBLISHER_PROOF;
            boolean acceptPublisherTestKey =
                    format == VerifierFormat.CRX3_WITH_TEST_PUBLISHER_PROOF;

            Set<String> requiredKeySet = new HashSet<>();
            for (byte[] keyHash : requiredKeyHashes) {
                requiredKeySet.add(ByteUtils.toHex(Arrays.copyOf(keyHash, keyHash.length)));
            }

            List<ActiveVerifier> verifiers = new ArrayList<>();
            boolean foundPublisherKey = false;
            String developerPublicKeyBase64 = null;

            ProofProcessingResult rsaResult = processProofs(header.getSha256WithRsaList(),
                    SignatureAlgorithm.RSA,
                    signedHeaderSize,
                    signedHeaderDataArray,
                    declaredCrxId,
                    requiredKeySet,
                    acceptPublisherTestKey,
                    verifiers);
            if (!rsaResult.success()) {
                return Verification.failure(rsaResult.error());
            }
            foundPublisherKey |= rsaResult.foundPublisherKey();
            developerPublicKeyBase64 = pickDeveloperKey(developerPublicKeyBase64, rsaResult.developerPublicKeyBase64());

            ProofProcessingResult ecdsaResult = processProofs(header.getSha256WithEcdsaList(),
                    SignatureAlgorithm.ECDSA,
                    signedHeaderSize,
                    signedHeaderDataArray,
                    declaredCrxId,
                    requiredKeySet,
                    acceptPublisherTestKey,
                    verifiers);
            if (!ecdsaResult.success()) {
                return Verification.failure(ecdsaResult.error());
            }
            foundPublisherKey |= ecdsaResult.foundPublisherKey();
            developerPublicKeyBase64 = pickDeveloperKey(developerPublicKeyBase64, ecdsaResult.developerPublicKeyBase64());

            if (developerPublicKeyBase64 == null || !requiredKeySet.isEmpty()) {
                return Verification.failure(VerifierResult.ERROR_REQUIRED_PROOF_MISSING);
            }

            if (requirePublisherKey && !foundPublisherKey) {
                return Verification.failure(VerifierResult.ERROR_REQUIRED_PROOF_MISSING);
            }

            byte[] verifiedContents = null;
            if (header.hasVerifiedContents()) {
                verifiedContents = header.getVerifiedContents().toByteArray();
            }

            if (!consumeArchive(input, fileHash, verifiers)) {
                return Verification.failure(VerifierResult.ERROR_SIGNATURE_VERIFICATION_FAILED);
            }

            byte[] fileDigest = fileHash.digest();
            if (requiredFileHash != null && requiredFileHash.length > 0) {
                if (requiredFileHash.length != fileDigest.length) {
                    return Verification.failure(VerifierResult.ERROR_EXPECTED_HASH_INVALID);
                }
                if (!MessageDigest.isEqual(requiredFileHash, fileDigest)) {
                    return Verification.failure(VerifierResult.ERROR_FILE_HASH_FAILED);
                }
            }

            VerifierResult successResult = diff ? VerifierResult.OK_DELTA : VerifierResult.OK_FULL;
            return Verification.success(successResult,
                    diff,
                    developerPublicKeyBase64,
                    declaredCrxId,
                    verifiedContents);
        } catch (EOFException e) {
            return Verification.failure(VerifierResult.ERROR_HEADER_INVALID);
        } catch (InvalidProtocolBufferException e) {
            return Verification.failure(VerifierResult.ERROR_HEADER_INVALID);
        } catch (NoSuchAlgorithmException e) {
            return Verification.failure(VerifierResult.ERROR_SIGNATURE_INITIALIZATION_FAILED);
        } catch (IOException e) {
            return Verification.failure(VerifierResult.ERROR_FILE_NOT_READABLE);
        }
    }

    private static String pickDeveloperKey(String current, Optional<String> candidate) {
        return candidate.orElse(current);
    }

    private static boolean consumeArchive(InputStream input,
                                          MessageDigest fileHash,
                                          List<ActiveVerifier> verifiers) {
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                fileHash.update(buffer, 0, read);
                for (ActiveVerifier verifier : verifiers) {
                    verifier.update(buffer, 0, read);
                }
            }
            for (ActiveVerifier verifier : verifiers) {
                if (!verifier.verify()) {
                    return false;
                }
            }
            return true;
        } catch (IOException | GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] readExactly(InputStream input, int length, MessageDigest hash)
            throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(buffer, offset, length - offset);
            if (read == -1) {
                throw new EOFException("Unexpected EOF");
            }
            hash.update(buffer, offset, read);
            offset += read;
        }
        return buffer;
    }

    private static int readLittleEndianInt(InputStream input, MessageDigest hash)
            throws IOException {
        byte[] bytes = readExactly(input, Integer.BYTES, hash);
        return ByteUtils.fromLittleEndian(bytes);
    }

    private static Crx3.CrxFileHeader parseHeader(byte[] headerBytes) throws InvalidProtocolBufferException {
        return Crx3.CrxFileHeader.parseFrom(headerBytes);
    }

    private static Crx3.SignedData parseSignedData(ByteString signedHeaderBytes)
            throws InvalidProtocolBufferException {
        return Crx3.SignedData.parseFrom(signedHeaderBytes);
    }

    private static ProofProcessingResult processProofs(List<Crx3.AsymmetricKeyProof> proofs,
                                                       SignatureAlgorithm algorithm,
                                                       byte[] signedHeaderSize,
                                                       byte[] signedHeaderData,
                                                       String declaredCrxId,
                                                       Set<String> requiredKeySet,
                                                       boolean acceptPublisherTestKey,
                                                       List<ActiveVerifier> verifiers) {
        boolean foundPublisherKey = false;
        String developerPublicKeyBase64 = null;
        for (Crx3.AsymmetricKeyProof proof : proofs) {
            byte[] publicKeyBytes = proof.getPublicKey().toByteArray();
            byte[] signatureBytes = proof.getSignature().toByteArray();

            PublicKey publicKey;
            try {
                KeyFactory factory = KeyFactory.getInstance(algorithm.keyFactoryAlgorithm);
                publicKey = factory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            } catch (GeneralSecurityException e) {
                return ProofProcessingResult.error(VerifierResult.ERROR_SIGNATURE_INITIALIZATION_FAILED);
            }

            Signature verifier;
            try {
                verifier = Signature.getInstance(algorithm.signatureAlgorithm);
                verifier.initVerify(publicKey);
                verifier.update(CrxConstants.SIGNATURE_CONTEXT);
                verifier.update(signedHeaderSize);
                verifier.update(signedHeaderData);
            } catch (GeneralSecurityException e) {
                return ProofProcessingResult.error(VerifierResult.ERROR_SIGNATURE_INITIALIZATION_FAILED);
            }
            verifiers.add(new ActiveVerifier(verifier, signatureBytes));

            byte[] keyHash = sha256(publicKeyBytes);
            requiredKeySet.remove(ByteUtils.toHex(keyHash));

            boolean publisherKey = Arrays.equals(keyHash, PUBLISHER_KEY_HASH)
                    || (acceptPublisherTestKey && Arrays.equals(keyHash, PUBLISHER_TEST_KEY_HASH));
            foundPublisherKey |= publisherKey;

            if (developerPublicKeyBase64 == null
                    && declaredCrxId.equals(IdUtil.generateId(publicKeyBytes))) {
                developerPublicKeyBase64 = Base64.getEncoder().encodeToString(publicKeyBytes);
            }
        }
        return ProofProcessingResult.success(foundPublisherKey, developerPublicKeyBase64);
    }

    private static byte[] sha256(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private enum SignatureAlgorithm {
        RSA("SHA256withRSA", "RSA"),
        ECDSA("SHA256withECDSA", "EC");

        final String signatureAlgorithm;
        final String keyFactoryAlgorithm;

        SignatureAlgorithm(String signatureAlgorithm, String keyFactoryAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
            this.keyFactoryAlgorithm = keyFactoryAlgorithm;
        }
    }

    private static final class ActiveVerifier {
        private final Signature signature;
        private final byte[] expectedSignature;

        ActiveVerifier(Signature signature, byte[] expectedSignature) {
            this.signature = signature;
            this.expectedSignature = expectedSignature;
        }

        void update(byte[] buffer, int offset, int length) throws SignatureException {
            signature.update(buffer, offset, length);
        }

        boolean verify() throws SignatureException {
            return signature.verify(expectedSignature);
        }
    }

    private record ProofProcessingResult(boolean success,
                                         boolean foundPublisherKey,
                                         Optional<String> developerPublicKeyBase64,
                                         VerifierResult error) {
        static ProofProcessingResult success(boolean foundPublisherKey, String developerPublicKeyBase64) {
            return new ProofProcessingResult(true,
                    foundPublisherKey,
                    Optional.ofNullable(developerPublicKeyBase64),
                    null);
        }

        static ProofProcessingResult error(VerifierResult error) {
            return new ProofProcessingResult(false, false, Optional.empty(), error);
        }
    }

    public static final class Verification {
        private final VerifierResult result;
        private final boolean delta;
        private final String publicKeyBase64;
        private final String crxId;
        private final byte[] compressedVerifiedContents;

        private Verification(VerifierResult result,
                             boolean delta,
                             String publicKeyBase64,
                             String crxId,
                             byte[] compressedVerifiedContents) {
            this.result = result;
            this.delta = delta;
            this.publicKeyBase64 = publicKeyBase64;
            this.crxId = crxId;
            this.compressedVerifiedContents = compressedVerifiedContents;
        }

        public static Verification success(VerifierResult result,
                                           boolean delta,
                                           String publicKeyBase64,
                                           String crxId,
                                           byte[] compressedVerifiedContents) {
            byte[] contentsCopy = compressedVerifiedContents == null ? null : compressedVerifiedContents.clone();
            return new Verification(result, delta, publicKeyBase64, crxId, contentsCopy);
        }

        public static Verification failure(VerifierResult result) {
            return new Verification(result, false, null, null, null);
        }

        public VerifierResult getResult() {
            return result;
        }

        public boolean isDelta() {
            return delta;
        }

        public Optional<String> getPublicKeyBase64() {
            return Optional.ofNullable(publicKeyBase64);
        }

        public Optional<String> getCrxId() {
            return Optional.ofNullable(crxId);
        }

        public Optional<byte[]> getCompressedVerifiedContents() {
            return compressedVerifiedContents == null
                    ? Optional.empty()
                    : Optional.of(compressedVerifiedContents.clone());
        }
    }
}
