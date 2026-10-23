package com.mearvk.securejdk.transition;

import java.io.*;
import java.security.*;
import java.util.Arrays;

/**
 * The STP-0001 secure session: the crypto handshake (§2.2) plus the sealed
 * record layer (§2.3). One instance drives one connection from either the
 * client or the server role.
 *
 * <p>After {@link #serverHandshake} / {@link #clientHandshake} completes, use
 * {@link #sendSealed} / {@link #recvSealed} to exchange authenticated,
 * encrypted frames. A region key (§4) is also derived for the ACK tag.
 */
public final class SecureSession {

    private final InputStream in;
    private final OutputStream out;
    private final byte[] tlsExporter;   // non-null on the remote/TLS path

    private byte[] kSend, kRecv, kRegion;
    private byte[] saltSend, saltRecv;  // 4-byte nonce salts (from HKDF)
    private long ctrSend = 0, ctrRecv = 0;

    private PublicKey peerIdentity;     // the authenticated peer Ed25519 key

    public SecureSession(InputStream in, OutputStream out, byte[] tlsExporter) {
        this.in = in; this.out = out; this.tlsExporter = tlsExporter;
    }

    public PublicKey peerIdentity() { return peerIdentity; }
    public byte[] regionKey() { return kRegion; }

    // ================= SERVER role =================

    /**
     * Perform the server side of the handshake.
     *
     * @param serverId       our long-term Ed25519 identity
     * @param authorizedClient predicate: is this raw client pubkey pinned/allowed?
     * @return the granted region hint echoed to derive the region key
     */
    public String serverHandshake(KeyPair serverId,
                                  java.util.function.Predicate<byte[]> authorizedClient)
            throws IOException, GeneralSecurityException {
        // --- receive HELLO_CLIENT (cleartext, signed) ---
        Wire.Header h = Wire.readHeader(in);
        if (h.version() != Stp.VERSION) { sendError(Stp.R_UNSUPPORTED_VERSION); throw new IOException("bad version"); }
        if (h.type() != Stp.T_HELLO_CLIENT) throw new IOException("expected HELLO_CLIENT, got " + Stp.typeName(h.type()));
        byte[] chBody = Wire.readFully(in, (int) h.bodyLen());
        Wire.Map2 ch = Wire.decode(chBody);

        String proto = ch.text("proto");
        if (!Stp.PROTO.equals(proto)) { sendError(Stp.R_UNSUPPORTED_VERSION); throw new IOException("bad proto"); }

        byte[] clientIdRaw  = ch.bytes("client_pub_ed25519");
        byte[] clientEphRaw = ch.bytes("client_eph_x25519");
        byte[] nonceC       = ch.bytes("nonce_c");
        byte[] sig          = ch.bytes("sig");
        String regionHint   = ch.textOr("region_hint", "default");

        if (!authorizedClient.test(clientIdRaw)) { sendError(Stp.R_UNKNOWN_PEER); throw new SecurityException("unknown client key"); }

        PublicKey clientId = Crypto.ed25519FromRaw(clientIdRaw);
        byte[] chSigned = signedPart(chBody, sig);   // body minus the sig field
        if (!Crypto.verify(clientId, chSigned, sig)) { sendError(Stp.R_CRYPTO_FAIL); throw new SecurityException("client sig"); }
        this.peerIdentity = clientId;

        // --- build + send HELLO_SERVER (cleartext, signed) ---
        KeyPair eph = Crypto.newX25519();
        byte[] serverEphRaw = Crypto.x25519Raw(eph.getPublic());
        byte[] nonceS = Crypto.randomBytes(24);
        String regionGrant = regionHint;
        byte[] policyDigest = Crypto.sha256(("policy|" + regionGrant).getBytes());

        Wire.Body shb = new Wire.Body()
                .put("server_pub_ed25519", Crypto.ed25519Raw(serverId.getPublic()))
                .put("server_eph_x25519", serverEphRaw)
                .put("nonce_s", nonceS)
                .put("region_grant", regionGrant)
                .put("policy_digest", policyDigest);
        byte[] shNoSig = shb.encode();
        byte[] shSig = Crypto.sign(serverId.getPrivate(), shNoSig);
        byte[] shBody = shb.put("sig", shSig).encode();
        Wire.writeFrame(out, Wire.header(Stp.T_HELLO_SERVER, 0, shBody.length), shBody);

        // --- derive keys ---
        PublicKey clientEph = Crypto.x25519FromRaw(clientEphRaw);
        byte[] ss = Crypto.x25519(eph.getPrivate(), clientEph);
        deriveKeys(ss, chBody, shBody, /*server=*/true);
        return regionGrant;
    }

    // ================= CLIENT role (for tests / reference) =================

    public void clientHandshake(KeyPair clientId, PublicKey pinnedServer, String regionHint)
            throws IOException, GeneralSecurityException {
        KeyPair eph = Crypto.newX25519();
        byte[] nonceC = Crypto.randomBytes(24);

        Wire.Body chb = new Wire.Body()
                .put("proto", Stp.PROTO)
                .put("client_pub_ed25519", Crypto.ed25519Raw(clientId.getPublic()))
                .put("client_eph_x25519", Crypto.x25519Raw(eph.getPublic()))
                .put("nonce_c", nonceC)
                .put("region_hint", regionHint);
        byte[] chNoSig = chb.encode();
        byte[] sig = Crypto.sign(clientId.getPrivate(), chNoSig);
        byte[] chBody = chb.put("sig", sig).encode();
        Wire.writeFrame(out, Wire.header(Stp.T_HELLO_CLIENT, 0, chBody.length), chBody);

        Wire.Header h = Wire.readHeader(in);
        if (h.type() != Stp.T_HELLO_SERVER) throw new IOException("expected HELLO_SERVER, got " + Stp.typeName(h.type()));
        byte[] shBody = Wire.readFully(in, (int) h.bodyLen());
        Wire.Map2 sh = Wire.decode(shBody);

        byte[] serverIdRaw = sh.bytes("server_pub_ed25519");
        PublicKey serverId = Crypto.ed25519FromRaw(serverIdRaw);
        if (pinnedServer != null && !Arrays.equals(Crypto.ed25519Raw(pinnedServer), serverIdRaw))
            throw new SecurityException("server key pin mismatch");
        byte[] shSig = sh.bytes("sig");
        if (!Crypto.verify(serverId, signedPart(shBody, shSig), shSig))
            throw new SecurityException("server sig");
        this.peerIdentity = serverId;

        PublicKey serverEph = Crypto.x25519FromRaw(sh.bytes("server_eph_x25519"));
        byte[] ss = Crypto.x25519(eph.getPrivate(), serverEph);
        deriveKeys(ss, chBody, shBody, /*server=*/false);
    }

    // ================= record layer =================

    public void sendSealed(int type, int flags, byte[] plaintext)
            throws IOException, GeneralSecurityException {
        byte[] nonce = nonce(saltSend, ctrSend++);
        // The sealed length is plaintext + 16-byte Poly1305 tag; the header
        // (with that final length) is the AEAD associated data, so the receiver
        // reconstructs identical AAD from the length it reads off the wire.
        int sealedLen = plaintext.length + 16;
        byte[] hdr = Wire.header(type, flags | Stp.F_ENCRYPTED, sealedLen);
        byte[] sealed = Crypto.seal(kSend, nonce, hdr, plaintext);   // AAD = header (final length)
        Wire.writeFrame(out, hdr, sealed);
    }

    /** Receive a sealed frame; returns [type, plaintextBody] via the holder. */
    public Received recvSealed() throws IOException, GeneralSecurityException {
        Wire.Header h = Wire.readHeader(in);
        byte[] sealed = Wire.readFully(in, (int) h.bodyLen());
        if (!h.encrypted()) {
            // ERROR frames may arrive in the clear.
            if (h.type() == Stp.T_ERROR) return new Received(h.type(), sealed);
            throw new IOException("expected encrypted frame, got clear " + Stp.typeName(h.type()));
        }
        byte[] hdr = Wire.header(h.type(), h.flags(), sealed.length);
        byte[] nonce = nonce(saltRecv, ctrRecv++);
        byte[] pt = Crypto.open(kRecv, nonce, hdr, sealed);          // throws on tamper
        return new Received(h.type(), pt);
    }

    public record Received(int type, byte[] body) {}

    public void sendError(String reason) {
        try {
            byte[] body = new Wire.Body().put("reason", reason).encode();
            Wire.writeFrame(out, Wire.header(Stp.T_ERROR, 0, body.length), body);
        } catch (IOException ignore) { /* best effort */ }
    }

    // ================= internals =================

    private void deriveKeys(byte[] ss, byte[] chBody, byte[] shBody, boolean server)
            throws GeneralSecurityException {
        byte[] th = Crypto.sha256(chBody, shBody);           // transcript hash (salt)
        byte[] ikm = (tlsExporter == null) ? ss : concat(ss, tlsExporter);

        byte[] c2s = Crypto.hkdf(ikm, th, Stp.INFO_C2S, 32);
        byte[] s2c = Crypto.hkdf(ikm, th, Stp.INFO_S2C, 32);
        this.kRegion = Crypto.hkdf(ikm, th, Stp.INFO_REGION, 32);

        // nonce salts: first 4 bytes of an HKDF expansion per direction
        byte[] saltC2s = Crypto.hkdf(ikm, th, "STP-0001|c2s-salt".getBytes(), 4);
        byte[] saltS2c = Crypto.hkdf(ikm, th, "STP-0001|s2c-salt".getBytes(), 4);

        if (server) {
            kRecv = c2s; kSend = s2c; saltRecv = saltC2s; saltSend = saltS2c;
        } else {
            kSend = c2s; kRecv = s2c; saltSend = saltC2s; saltRecv = saltS2c;
        }
    }

    private static byte[] nonce(byte[] salt4, long counter) {
        byte[] n = new byte[12];
        System.arraycopy(salt4, 0, n, 0, 4);
        for (int i = 0; i < 8; i++) n[4 + i] = (byte) (counter >>> (56 - 8 * i)); // big-endian counter
        return n;
    }

    /** Re-encode the body without the trailing "sig" entry (deterministic order). */
    private static byte[] signedPart(byte[] fullBody, byte[] sig) {
        // The body was encoded with "sig" appended last; recompute the pre-sig
        // encoding by decoding and re-encoding all entries except "sig".
        Wire.Map2 m = Wire.decode(fullBody);
        Wire.Body rebuilt = new Wire.Body();
        // Preserve original key order by re-reading raw. Simpler: the two hello
        // encoders always put "sig" last, so strip the last entry deterministically.
        return stripLastEntry(fullBody);
    }

    /** Remove the final map entry (the signature) from a flat-map body. */
    private static byte[] stripLastEntry(byte[] body) {
        int p = 0;
        int n = Wire.getU16LE(body, p); p += 2;
        int start = p;
        int lastStart = p;
        for (int i = 0; i < n; i++) {
            lastStart = p;
            int klen = body[p++] & 0xff; p += klen;
            int vt = body[p++] & 0xff;
            switch (vt) {
                case Wire.VT_BYTES, Wire.VT_TEXT -> { int len = (int) Wire.getU32LE(body, p); p += 4 + len; }
                case Wire.VT_U64 -> p += 8;
                case Wire.VT_BOOL -> p += 1;
                default -> throw new IllegalStateException("bad vt");
            }
        }
        // Rebuild with count n-1, entries [start, lastStart)
        byte[] head = new byte[2];
        Wire.putU16LE(head, 0, n - 1);
        int entriesLen = lastStart - start;
        byte[] out = new byte[2 + entriesLen];
        System.arraycopy(head, 0, out, 0, 2);
        System.arraycopy(body, start, out, 2, entriesLen);
        return out;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] c = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }
}
