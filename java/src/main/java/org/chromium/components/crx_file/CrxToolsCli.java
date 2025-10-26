package org.chromium.components.crx_file;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class CrxToolsCli {
    private static final int DEFAULT_RSA_KEY_SIZE = 2048;

    private final PrintStream out;
    private final PrintStream err;

    public CrxToolsCli() {
        this(System.out, System.err);
    }

    public CrxToolsCli(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out);
        this.err = Objects.requireNonNull(err);
    }

    public static void main(String[] args) {
        CrxToolsCli cli = new CrxToolsCli();
        int exitCode = cli.run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public int run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return 1;
        }
        String command = args[0].toLowerCase(Locale.ROOT);
        String[] commandArgs = Arrays.copyOfRange(args, 1, args.length);
        return switch (command) {
            case "create" -> runCreate(commandArgs);
            case "verify" -> runVerify(commandArgs);
            case "help", "-h", "--help" -> {
                printUsage();
                yield 0;
            }
            default -> {
                err.println("Unknown command: " + command);
                printUsage();
                yield 1;
            }
        };
    }

    private int runCreate(String[] args) {
        Path source = null;
        Path output = null;
        Path privateKeyPath = null;
        Path verifiedContentsPath = null;
        boolean verbose = false;

        int index = 0;
        if (index < args.length && !args[index].startsWith("-")) {
            source = Path.of(args[index++]);
        }

        while (index < args.length) {
            String arg = args[index++];
            switch (arg) {
                case "--source" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printCreateUsage);
                    }
                    source = Path.of(args[index++]);
                }
                case "--output", "-o" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printCreateUsage);
                    }
                    output = Path.of(args[index++]);
                }
                case "--private-key", "-pk" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printCreateUsage);
                    }
                    privateKeyPath = Path.of(args[index++]);
                }
                case "--verified-contents" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printCreateUsage);
                    }
                    verifiedContentsPath = Path.of(args[index++]);
                }
                case "--verbose", "-v" -> verbose = true;
                case "--help", "-h" -> {
                    printCreateUsage();
                    return 0;
                }
                default -> {
                    err.println("Unknown option: " + arg);
                    printCreateUsage();
                    return 1;
                }
            }
        }

        if (source == null) {
            err.println("Missing source path");
            printCreateUsage();
            return 1;
        }
        if (!Files.exists(source)) {
            err.println("Source path does not exist: " + source);
            return 1;
        }

        if (output == null) {
            output = defaultOutputPath(source);
        }

        PrivateKey privateKey;
        boolean generatedKey = false;
        Path effectiveKeyPath;
        try {
            if (privateKeyPath != null) {
                privateKey = KeyUtils.readPkcs8PrivateKey(privateKeyPath);
                effectiveKeyPath = privateKeyPath;
            } else {
                effectiveKeyPath = defaultPrivateKeyPath(output);
                KeyPair keyPair = KeyUtils.generateRsaKeyPair(DEFAULT_RSA_KEY_SIZE);
                privateKey = keyPair.getPrivate();
                KeyUtils.writePkcs8PrivateKey(effectiveKeyPath, privateKey);
                generatedKey = true;
            }
        } catch (IOException | GeneralSecurityException e) {
            err.println("Failed to prepare private key: " + e.getMessage());
            return 1;
        }

        byte[] verifiedContentsBytes = null;
        if (verifiedContentsPath != null) {
            try {
                verifiedContentsBytes = Files.readAllBytes(verifiedContentsPath);
            } catch (IOException e) {
                err.println("Failed to read verified contents file: " + e.getMessage());
                return 1;
            }
        }

        Path temporaryZip = null;
        try {
            Path archivePath;
            if (Files.isDirectory(source)) {
                temporaryZip = Files.createTempFile("crx-source-", ".zip");
                zipDirectory(source, temporaryZip);
                archivePath = temporaryZip;
            } else {
                archivePath = source;
            }

            Path parent = output.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            CreatorResult result;
            if (verifiedContentsBytes != null) {
                result = CrxCreator.createWithVerifiedContents(output, archivePath, privateKey, verifiedContentsBytes);
            } else {
                result = CrxCreator.create(output, archivePath, privateKey);
            }
            if (result != CreatorResult.OK) {
                err.println("Failed to create CRX: " + result);
                return 1;
            }

            out.println("OK");
            out.println("CRX file written to: " + output);
            out.println("Private key used: " + effectiveKeyPath);
            if (generatedKey) {
                out.println("A new private key was generated.");
            }

            if (verbose) {
                CrxVerifier.Verification verification =
                        CrxVerifier.verify(output, VerifierFormat.CRX3, List.of(), new byte[0]);
                if (verification.getResult() == VerifierResult.OK_FULL || verification.getResult() == VerifierResult.OK_DELTA) {
                    verification.getCrxId().ifPresent(id -> out.println("CRX ID: " + id));
                    verification.getPublicKeyBase64().ifPresent(key -> out.println("Public key (base64): " + key));
                } else {
                    out.println("Warning: immediate verification failed with status " + verification.getResult());
                }
            }
            return 0;
        } catch (IOException e) {
            err.println("Failed to create CRX: " + e.getMessage());
            return 1;
        } finally {
            if (temporaryZip != null) {
                try {
                    Files.deleteIfExists(temporaryZip);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            }
        }
    }

    private int runVerify(String[] args) {
        Path crxPath = null;
        VerifierFormat format = VerifierFormat.CRX3;
        List<byte[]> requiredKeyHashes = new ArrayList<>();
        byte[] requiredFileHash = new byte[0];
        Path verifiedContentsOut = null;
        Path publicKeyOut = null;
        boolean quiet = false;

        int index = 0;
        if (index < args.length && !args[index].startsWith("-")) {
            crxPath = Path.of(args[index++]);
        }

        while (index < args.length) {
            String arg = args[index++];
            switch (arg) {
                case "--crx" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printVerifyUsage);
                    }
                    crxPath = Path.of(args[index++]);
                }
                case "--format" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printVerifyUsage);
                    }
                    String value = args[index++].toLowerCase(Locale.ROOT);
                    format = switch (value) {
                        case "crx3" -> VerifierFormat.CRX3;
                        case "crx3-with-test-proof", "crx3-test" -> VerifierFormat.CRX3_WITH_TEST_PUBLISHER_PROOF;
                        case "crx3-with-proof", "crx3-prod" -> VerifierFormat.CRX3_WITH_PUBLISHER_PROOF;
                        default -> {
                            err.println("Unknown format: " + value);
                            printVerifyUsage();
                            yield null;
                        }
                    };
                    if (format == null) {
                        return 1;
                    }
                }
                case "--required-key-hash" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printVerifyUsage);
                    }
                    try {
                        requiredKeyHashes.add(ByteUtils.fromHex(args[index++].toLowerCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        err.println("Invalid key hash: " + e.getMessage());
                        return 1;
                    }
                }
                case "--required-file-hash" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printVerifyUsage);
                    }
                    try {
                        requiredFileHash = ByteUtils.fromHex(args[index++].toLowerCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        err.println("Invalid file hash: " + e.getMessage());
                        return 1;
                    }
                }
                case "--verified-contents-out" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printVerifyUsage);
                    }
                    verifiedContentsOut = Path.of(args[index++]);
                }
                case "--public-key-out" -> {
                    if (index >= args.length) {
                        return missingValue(arg, this::printVerifyUsage);
                    }
                    publicKeyOut = Path.of(args[index++]);
                }
                case "--quiet", "-q" -> quiet = true;
                case "--help", "-h" -> {
                    printVerifyUsage();
                    return 0;
                }
                default -> {
                    err.println("Unknown option: " + arg);
                    printVerifyUsage();
                    return 1;
                }
            }
        }

        if (crxPath == null) {
            err.println("Missing CRX path");
            printVerifyUsage();
            return 1;
        }
        if (!Files.exists(crxPath)) {
            err.println("CRX file does not exist: " + crxPath);
            return 1;
        }

        CrxVerifier.Verification verification =
                CrxVerifier.verify(crxPath, format, requiredKeyHashes, requiredFileHash);

        VerifierResult result = verification.getResult();
        if (result != VerifierResult.OK_FULL && result != VerifierResult.OK_DELTA) {
            err.println(result.name());
            return 1;
        }

        if (!quiet) {
            out.println(result.name());
            verification.getCrxId().ifPresent(id -> out.println("CRX ID: " + id));
            verification.getPublicKeyBase64().ifPresent(key -> out.println("Public key (base64): " + key));
            out.println("Delta update: " + verification.isDelta());
            verification.getCompressedVerifiedContents()
                    .ifPresent(bytes -> out.println("Verified contents bytes: " + bytes.length));
        }

        try {
            if (publicKeyOut != null) {
                Optional<String> publicKey = verification.getPublicKeyBase64();
                if (publicKey.isPresent()) {
                    Path parent = publicKeyOut.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(publicKeyOut, publicKey.get(), StandardCharsets.UTF_8);
                } else {
                    err.println("No public key available to write");
                }
            }
            if (verifiedContentsOut != null) {
                Optional<byte[]> contents = verification.getCompressedVerifiedContents();
                if (contents.isPresent()) {
                    Path parent = verifiedContentsOut.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.write(verifiedContentsOut, contents.get());
                } else {
                    err.println("No verified contents were present in the CRX header");
                }
            }
        } catch (IOException e) {
            err.println("Failed to write output file: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    private int missingValue(String option, Runnable usagePrinter) {
        err.println("Missing value for option " + option);
        usagePrinter.run();
        return 1;
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

    private void addZipEntry(ZipOutputStream zipOutputStream, Path sourceDir, Path file) {
        ZipEntry entry = new ZipEntry(sourceDir.relativize(file).toString().replace('\\', '/'));
        try {
            zipOutputStream.putNextEntry(entry);
            Files.copy(file, zipOutputStream);
            zipOutputStream.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path defaultOutputPath(Path source) {
        String fileName = source.getFileName().toString();
        if (fileName.endsWith(".zip")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        return source.resolveSibling(fileName + ".crx");
    }

    private Path defaultPrivateKeyPath(Path output) {
        String fileName = output.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        if (index > 0) {
            fileName = fileName.substring(0, index);
        }
        return output.resolveSibling(fileName + ".pem");
    }

    private void printUsage() {
        out.println("Usage: java -jar crx-tools.jar <command> [options]\n");
        out.println("Commands:");
        out.println("  create    Package a directory or ZIP archive into a CRX3 file");
        out.println("  verify    Verify the integrity of a CRX3 file");
        out.println("  help      Show this message");
    }

    private void printCreateUsage() {
        out.println("Usage: create [source] [options]\n" +
                "Options:\n" +
                "  --source <path>             Directory or ZIP archive to package\n" +
                "  --output, -o <path>         Output CRX file (defaults to <source>.crx)\n" +
                "  --private-key, -pk <path>   PKCS#8 PEM encoded RSA private key\n" +
                "  --verified-contents <path>  Optional verified contents blob to embed\n" +
                "  --verbose, -v               Print additional information\n" +
                "  --help, -h                  Show this help message");
    }

    private void printVerifyUsage() {
        out.println("Usage: verify [crx] [options]\n" +
                "Options:\n" +
                "  --crx <path>                      Path to the CRX file\n" +
                "  --format <crx3|crx3-with-test-proof|crx3-with-proof>\n" +
                "                                     Verification mode (default: crx3)\n" +
                "  --required-key-hash <hex>         Require a proof from the given key hash\n" +
                "  --required-file-hash <hex>        Require the file hash to match\n" +
                "  --public-key-out <path>           Write the developer public key (base64)\n" +
                "  --verified-contents-out <path>    Write the compressed verified contents\n" +
                "  --quiet, -q                       Suppress informational output\n" +
                "  --help, -h                        Show this help message");
    }
}
