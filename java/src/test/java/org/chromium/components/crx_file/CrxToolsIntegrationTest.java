package org.chromium.components.crx_file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrxToolsIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void createAndVerifyRoundTrip() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();

        Path sourceDir = repositoryRoot().resolve("chrome-mvp-extension");
        Path zipFile = tempDir.resolve("extension.zip");
        zipDirectory(sourceDir, zipFile);

        Path outputCrx = tempDir.resolve("extension.crx");
        CreatorResult createResult = CrxCreator.create(outputCrx, zipFile, keyPair.getPrivate());
        assertEquals(CreatorResult.OK, createResult);

        CrxVerifier.Verification verification =
                CrxVerifier.verify(outputCrx, VerifierFormat.CRX3, List.of(), new byte[0]);
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
        assertFalse(verification.isDelta());

        Optional<String> publicKey = verification.getPublicKeyBase64();
        assertTrue(publicKey.isPresent());
        assertEquals(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                publicKey.get());

        Optional<String> crxId = verification.getCrxId();
        assertTrue(crxId.isPresent());
        assertEquals(IdUtil.generateId(keyPair.getPublic().getEncoded()), crxId.get());

        if (isCrx3Available()) {
            Process process = new ProcessBuilder("crx3", "verify", outputCrx.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            assertTrue(finished, "crx3 verification timed out");
            assertEquals(0, process.exitValue(), () -> "crx3 verify failed: " + output.trim());
            assertTrue(output.contains("OK_FULL"), "crx3 output did not include OK_FULL");
        }
    }

    @Test
    void verifyCliGeneratedCrx() throws IOException {
        Path cliCrx = repositoryRoot().resolve("chrome-mvp-extension.crx");
        Assumptions.assumeTrue(Files.exists(cliCrx), "Expected CLI generated CRX to exist");
        CrxVerifier.Verification verification =
                CrxVerifier.verify(cliCrx, VerifierFormat.CRX3, List.of(), new byte[0]);
        assertEquals(VerifierResult.OK_FULL, verification.getResult());
        Optional<String> crxId = verification.getCrxId();
        assertTrue(crxId.isPresent());
        assertTrue(IdUtil.isValid(crxId.get()));
    }

    private static KeyPair generateRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private void zipDirectory(Path sourceDir, Path zipFile) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile));
             Stream<Path> stream = Files.walk(sourceDir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> addZipEntry(zipOutputStream, sourceDir, path));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    private static void addZipEntry(ZipOutputStream zipOutputStream, Path sourceDir, Path file) {
        ZipEntry entry = new ZipEntry(sourceDir.relativize(file).toString().replace('\\', '/'));
        try {
            zipOutputStream.putNextEntry(entry);
            Files.copy(file, zipOutputStream);
            zipOutputStream.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path repositoryRoot() {
        Path moduleRoot = Path.of("").toAbsolutePath().normalize();
        Path parent = moduleRoot.getParent();
        if (parent == null) {
            throw new IllegalStateException("Unable to determine repository root");
        }
        return parent;
    }

    private static boolean isCrx3Available() {
        ProcessBuilder builder = new ProcessBuilder("which", "crx3");
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
