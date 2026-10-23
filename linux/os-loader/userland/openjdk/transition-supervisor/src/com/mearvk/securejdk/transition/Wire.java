package com.mearvk.securejdk.transition;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * STP framing + a tiny portable flat-map body codec.
 *
 * <p>Frame header (§3.1) is 8 bytes little-endian:
 * {@code version(u8) | type(u8) | flags(u16) | body_len(u32)}.
 *
 * <p>Bodies use a deliberately simple, C-portable TLV map: a u16 count of
 * entries, then for each entry a u8 key-length + key bytes + u8 value-type
 * + value. Value types: 0=bytes(u32 len), 1=text(u32 len, UTF-8),
 * 2=u64(8 bytes LE), 3=bool(1 byte). This avoids pulling in a CBOR library
 * while staying trivially decodable from C.
 */
public final class Wire {
    private Wire() {}

    public static final int VT_BYTES = 0, VT_TEXT = 1, VT_U64 = 2, VT_BOOL = 3;

    /** A decoded frame header. */
    public record Header(int version, int type, int flags, long bodyLen) {
        public boolean encrypted() { return (flags & Stp.F_ENCRYPTED) != 0; }
    }

    // ---- header --------------------------------------------------------

    public static byte[] header(int type, int flags, int bodyLen) {
        byte[] h = new byte[Stp.HEADER_LEN];
        h[0] = (byte) Stp.VERSION;
        h[1] = (byte) type;
        putU16LE(h, 2, flags);
        putU32LE(h, 4, bodyLen);
        return h;
    }

    public static Header readHeader(InputStream in) throws IOException {
        byte[] h = readFully(in, Stp.HEADER_LEN);
        int ver   = h[0] & 0xff;
        int type  = h[1] & 0xff;
        int flags = getU16LE(h, 2);
        long len  = getU32LE(h, 4);
        return new Header(ver, type, flags, len);
    }

    public static void writeFrame(OutputStream out, byte[] hdr, byte[] body) throws IOException {
        out.write(hdr);
        if (body != null && body.length > 0) out.write(body);
        out.flush();
    }

    // ---- flat-map body codec ------------------------------------------

    /** Ordered map body builder. */
    public static final class Body {
        private final Map<String, Object> m = new LinkedHashMap<>();
        public Body put(String k, byte[] v)  { m.put(k, v); return this; }
        public Body put(String k, String v)  { m.put(k, v); return this; }
        public Body put(String k, long v)    { m.put(k, v); return this; }
        public Body put(String k, boolean v) { m.put(k, v); return this; }

        public byte[] encode() {
            try {
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                putU16LE(b, m.size());
                for (Map.Entry<String, Object> e : m.entrySet()) {
                    byte[] key = e.getKey().getBytes(StandardCharsets.UTF_8);
                    b.write(key.length & 0xff);
                    b.write(key);
                    Object v = e.getValue();
                    if (v instanceof byte[] bytes) {
                        b.write(VT_BYTES); putU32LE(b, bytes.length); b.write(bytes);
                    } else if (v instanceof String s) {
                        byte[] t = s.getBytes(StandardCharsets.UTF_8);
                        b.write(VT_TEXT); putU32LE(b, t.length); b.write(t);
                    } else if (v instanceof Long l) {
                        b.write(VT_U64); putU64LE(b, l);
                    } else if (v instanceof Boolean bo) {
                        b.write(VT_BOOL); b.write(bo ? 1 : 0);
                    } else {
                        throw new IllegalStateException("bad value type for " + e.getKey());
                    }
                }
                return b.toByteArray();
            } catch (IOException io) {
                throw new UncheckedIOException(io);
            }
        }
    }

    /** A decoded map body. */
    public static final class Map2 {
        private final Map<String, Object> m;
        Map2(Map<String, Object> m) { this.m = m; }
        public boolean has(String k)      { return m.containsKey(k); }
        public byte[] bytes(String k)     { return (byte[]) m.get(k); }
        public String text(String k)      { return (String) m.get(k); }
        public long u64(String k)         { return (Long) m.get(k); }
        public boolean bool(String k)     { return (Boolean) m.get(k); }
        public String textOr(String k, String d) { return m.containsKey(k) ? (String) m.get(k) : d; }
        public long u64Or(String k, long d)      { return m.containsKey(k) ? (Long) m.get(k) : d; }
    }

    public static Map2 decode(byte[] body) {
        Map<String, Object> m = new LinkedHashMap<>();
        int[] p = {0};
        int n = getU16LE(body, p[0]); p[0] += 2;
        for (int i = 0; i < n; i++) {
            int klen = body[p[0]++] & 0xff;
            String key = new String(body, p[0], klen, StandardCharsets.UTF_8); p[0] += klen;
            int vt = body[p[0]++] & 0xff;
            switch (vt) {
                case VT_BYTES -> {
                    int len = (int) getU32LE(body, p[0]); p[0] += 4;
                    byte[] v = new byte[len];
                    System.arraycopy(body, p[0], v, 0, len); p[0] += len;
                    m.put(key, v);
                }
                case VT_TEXT -> {
                    int len = (int) getU32LE(body, p[0]); p[0] += 4;
                    m.put(key, new String(body, p[0], len, StandardCharsets.UTF_8)); p[0] += len;
                }
                case VT_U64 -> { m.put(key, getU64LE(body, p[0])); p[0] += 8; }
                case VT_BOOL -> { m.put(key, body[p[0]++] != 0); }
                default -> throw new IllegalStateException("bad value type " + vt + " for " + key);
            }
        }
        return new Map2(m);
    }

    // ---- little-endian helpers ----------------------------------------

    public static void putU16LE(byte[] a, int o, int v) { a[o] = (byte) v; a[o + 1] = (byte) (v >> 8); }
    public static void putU32LE(byte[] a, int o, long v) {
        a[o] = (byte) v; a[o + 1] = (byte) (v >> 8); a[o + 2] = (byte) (v >> 16); a[o + 3] = (byte) (v >> 24);
    }
    public static int getU16LE(byte[] a, int o) { return (a[o] & 0xff) | ((a[o + 1] & 0xff) << 8); }
    public static long getU32LE(byte[] a, int o) {
        return (a[o] & 0xffL) | ((a[o + 1] & 0xffL) << 8) | ((a[o + 2] & 0xffL) << 16) | ((a[o + 3] & 0xffL) << 24);
    }
    public static long getU64LE(byte[] a, int o) {
        long v = 0; for (int i = 0; i < 8; i++) v |= (a[o + i] & 0xffL) << (8 * i); return v;
    }
    static void putU16LE(OutputStream o, int v) throws IOException { o.write(v & 0xff); o.write((v >> 8) & 0xff); }
    static void putU32LE(OutputStream o, long v) throws IOException {
        o.write((int) (v & 0xff)); o.write((int) ((v >> 8) & 0xff));
        o.write((int) ((v >> 16) & 0xff)); o.write((int) ((v >> 24) & 0xff));
    }
    static void putU64LE(OutputStream o, long v) throws IOException {
        for (int i = 0; i < 8; i++) o.write((int) ((v >> (8 * i)) & 0xff));
    }
    public static byte[] u64le(long v) { byte[] a = new byte[8]; for (int i = 0; i < 8; i++) a[i] = (byte)(v >> (8*i)); return a; }

    public static byte[] readFully(InputStream in, int n) throws IOException {
        byte[] b = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(b, off, n - off);
            if (r < 0) throw new EOFException("STP: stream closed mid-frame (need " + n + ", got " + off + ")");
            off += r;
        }
        return b;
    }
}
