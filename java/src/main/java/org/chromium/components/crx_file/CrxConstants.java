package org.chromium.components.crx_file;

import java.nio.charset.StandardCharsets;

final class CrxConstants {
    static final byte[] MAGIC_FULL = {'C', 'r', '2', '4'};
    static final byte[] MAGIC_DIFF = {'C', 'r', 'O', 'D'};
    static final int CRX3_VERSION = 3;
    static final byte[] SIGNATURE_CONTEXT = "CRX3 SignedData\0".getBytes(StandardCharsets.US_ASCII);
    static final byte[] ZIP_EOCD = {'P', 'K', 0x05, 0x06};
    static final byte[] ZIP_EOCD64 = {'P', 'K', 0x06, 0x07};

    private CrxConstants() {}
}
