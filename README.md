# CRX Tools

This repository contains utilities for creating and validating Chrome CRX3 extension packages. It includes:

- **`crx_file/`** – the original Chromium C++ implementation that provides low-level primitives for CRX creation, verification, and extension ID handling.
- **`java/`** – a Maven module that exposes the same functionality in Java, complete with a command-line interface (CLI) and JUnit tests.
- **`chrome-mvp-extension/`** – a sample Chrome extension used throughout the test fixtures.

The Java implementation mirrors the behaviour of the C++ code and is integration-tested against the official `crx3` Python CLI to guarantee interoperability.

## Prerequisites

The tooling expects the following software to be available on your machine:

- Java 17 (OpenJDK 17 or newer)
- Apache Maven 3.8+
- The reference `crx3` CLI (Python implementation) for cross-checks
- Protocol Buffers compiler (`protoc`)

On Ubuntu/Debian based systems you can install everything with:

```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk maven protobuf-compiler
uv tool install crx3
```

`uv` installs the Python-based `crx3` tool inside an isolated environment and makes it available on your `$PATH`.

## Building the Java module

From the project root:

```bash
cd java
mvn package
```

This command compiles the Java sources, runs the test suite, and produces a shaded, executable JAR at:

```
java/target/crx-tools-1.0-SNAPSHOT-all.jar
```

The shaded JAR bundles all runtime dependencies, so it can be executed directly with `java -jar`.

## CLI usage

The Java CLI intentionally mirrors the ergonomics of the Python `crx3` tool while staying close to the C++ APIs. Run `java -jar ... help` to view the short summary, or use the commands documented below.

### Creating a CRX package

```
java -jar target/crx-tools-1.0-SNAPSHOT-all.jar create <source> [options]
```

- `<source>` can be either a directory or a `.zip` archive containing your extension files.
- `--output, -o <path>` specifies the output CRX file. Defaults to `<source>.crx`.
- `--private-key, -pk <path>` points to a PKCS#8 PEM encoded RSA private key. If omitted, a fresh 2048-bit key is generated and stored next to the output CRX (for example `extension.pem`).
- `--verified-contents <path>` embeds an optional verified-contents blob.
- `--verbose` prints additional metadata such as the derived CRX ID and developer key.

Example:

```bash
java -jar target/crx-tools-1.0-SNAPSHOT-all.jar create chrome-mvp-extension \
  --output out/chrome-mvp-extension.crx \
  --private-key keys/developer.pem \
  --verbose
```

If you omit `--private-key`, the CLI writes the newly generated key next to the CRX file so subsequent builds can reuse it.

### Verifying a CRX package

```
java -jar target/crx-tools-1.0-SNAPSHOT-all.jar verify <crx> [options]
```

- `--format <crx3|crx3-with-test-proof|crx3-with-proof>` selects the verification mode (default: `crx3`).
- `--required-key-hash <hex>` can be repeated to demand proofs for specific key hashes (hex-encoded SHA256).
- `--required-file-hash <hex>` checks that the file hash matches the supplied digest.
- `--public-key-out <path>` writes the developer public key (base64) to a file.
- `--verified-contents-out <path>` stores the compressed verified-contents payload if present.
- `--quiet` suppresses the informational output (useful for scripting).

The command exits with a zero status when the CRX validates (`OK_FULL` or `OK_DELTA`), and prints the result to standard output.

Example:

```bash
java -jar target/crx-tools-1.0-SNAPSHOT-all.jar verify out/chrome-mvp-extension.crx \
  --public-key-out out/public_key.b64
```

## Interoperability with the Python `crx3` tool

The test suite exercises round-trips between the Java implementation and the reference Python CLI. If `crx3` is available on the `$PATH`, the tests verify that:

- CRX files created by the Java CLI pass validation in the Python CLI.
- CRX files created by the Python CLI validate correctly with the Java verifier and CLI.

You can perform the same checks manually, for example:

```bash
crx3 create chrome-mvp-extension -o extension.crx
java -jar target/crx-tools-1.0-SNAPSHOT-all.jar verify extension.crx
```

and conversely:

```bash
java -jar target/crx-tools-1.0-SNAPSHOT-all.jar create chrome-mvp-extension --output extension-java.crx
crx3 verify extension-java.crx
```

## Running tests

To execute the Java unit and integration tests:

```bash
cd java
mvn test
```

The tests cover the core `CrxCreator`, `CrxVerifier`, the ID utilities, and the new CLI surface.

## Directory summary

| Path | Description |
| ---- | ----------- |
| `crx_file/` | Original Chromium C++ sources, kept for reference. |
| `java/src/main/java/` | Java implementations of the CRX creator, verifier, CLI, and helpers. |
| `java/src/main/proto/` | Protocol Buffer definitions shared across languages. |
| `java/src/test/java/` | JUnit tests and integration checks. |
| `chrome-mvp-extension/` | Sample Chrome extension fixtures. |

## Additional notes

- Private keys must be supplied in unencrypted PKCS#8 PEM format when using the Java CLI. The tool automatically generates a compliant key when none is provided.
- Verified-contents blobs are treated as opaque byte arrays and must be pre-compressed according to Chrome's expectations if you choose to embed them.
- The shaded JAR produced by the build contains the CLI entry point and can be distributed independently of the source tree.
