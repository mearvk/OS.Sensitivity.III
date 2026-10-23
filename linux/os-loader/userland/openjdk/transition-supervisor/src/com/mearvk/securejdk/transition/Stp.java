package com.mearvk.securejdk.transition;

/**
 * STP-0001 wire constants — the Sleela Transition Protocol.
 *
 * <p>This is the single normative source of the frame format and the crypto
 * labels shared by the SecureJDK 28 supervisor (this package) and the Sleela
 * client (C/C++ side in the SLeeLa repo). See {@code impl/transition/STP.model}
 * in the SLeeLa repo for the full specification.
 */
public final class Stp {
    private Stp() {}

    /** Protocol identity string, mixed into the handshake transcript. */
    public static final String PROTO = "STP-0001";

    /** Frame header wire version. */
    public static final int VERSION = 1;

    /** Fixed frame header size in bytes (little-endian). */
    public static final int HEADER_LEN = 8;

    // ---- Frame types (header byte 1) --------------------------------------
    public static final int T_HELLO_CLIENT   = 0x01;
    public static final int T_HELLO_SERVER   = 0x02;
    public static final int T_TRANSITION_REQ = 0x10;
    public static final int T_ACK            = 0x11;
    public static final int T_DENY           = 0x12;
    public static final int T_HEARTBEAT      = 0x13;
    public static final int T_CLOSE          = 0x1E;
    public static final int T_ERROR          = 0x1F;

    // ---- Frame flags (header u16 at offset 2) -----------------------------
    public static final int F_ENCRYPTED = 0x0001;
    public static final int F_LAST      = 0x0002;

    // ---- HKDF info labels (§2.2) ------------------------------------------
    public static final byte[] INFO_C2S    = "STP-0001|c2s".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    public static final byte[] INFO_S2C    = "STP-0001|s2c".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    public static final byte[] INFO_REGION = "STP-0001|region".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /** TLS exporter label for the remote (TLS 1.3) channel binding. */
    public static final String TLS_EXPORTER_LABEL = "EXPORTER-STP-v1";

    // ---- Defaults ----------------------------------------------------------
    public static final String DEFAULT_PIPE_PRIMARY  = "/run/sleela/stp.sock";
    public static final String DEFAULT_PIPE_FALLBACK = "/tmp/sleela-stp.sock";
    public static final int    DEFAULT_REMOTE_PORT   = 8443;
    public static final int    DEFAULT_TIMEOUT_MS    = 3000;
    public static final int    DEFAULT_HEARTBEAT_MS  = 1000;

    // ---- DENY / ERROR reason codes ----------------------------------------
    public static final String R_BUDGET_EXCEEDED    = "BUDGET_EXCEEDED";
    public static final String R_GRADE_DENIED       = "GRADE_DENIED";
    public static final String R_UNKNOWN_PEER       = "UNKNOWN_PEER";
    public static final String R_POLICY             = "POLICY";
    public static final String R_CRYPTO_FAIL        = "CRYPTO_FAIL";
    public static final String R_TIMEOUT            = "TIMEOUT";
    public static final String R_UNREACHABLE        = "UNREACHABLE";
    public static final String R_UNSUPPORTED_VERSION= "UNSUPPORTED_VERSION";

    public static String typeName(int t) {
        return switch (t) {
            case T_HELLO_CLIENT   -> "HELLO_CLIENT";
            case T_HELLO_SERVER   -> "HELLO_SERVER";
            case T_TRANSITION_REQ -> "TRANSITION_REQ";
            case T_ACK            -> "ACK";
            case T_DENY           -> "DENY";
            case T_HEARTBEAT      -> "HEARTBEAT";
            case T_CLOSE          -> "CLOSE";
            case T_ERROR          -> "ERROR";
            default               -> "UNKNOWN(0x" + Integer.toHexString(t) + ")";
        };
    }
}
