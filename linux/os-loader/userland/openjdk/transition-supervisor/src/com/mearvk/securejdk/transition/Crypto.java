package com.mearvk.securejdk.transition;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.interfaces.EdECPublicKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.*;
import java.util.Arrays;

/**
 * State-of-the-art crypto primitives for STP-0001, built entirely on JDK APIs
 * so the SecureJDK 28 supervisor needs no third-party crypto library:
 *
 * <ul>
 *   <li>Ed25519  — long-term identity signatures</li>
 *   <li>X25519   — ephemeral key agreement (forward secrecy)</li>
 *   <li>HKDF-SHA-256 — key derivation (RFC 5869), implemented over HmacSHA256</li>
 *   <li>ChaCha20-Poly1305 (IETF) — AEAD record encryption</li>
 *   <li>SHA-256 — transcript hashing</li>
 * </ul>
 *
 * Raw 32-byte public-key encodings are used on the wire (little-endian u-coords
 * for X25519, the standard 32-byte Ed25519 encoding) so the C client can
 * interoperate byte-for-byte.
 */
public final class Crypto {
    private Crypto() {}

    private static final SecureRandom RNG = new SecureRandom();

    // ---- Ed25519 identity --------------------------------------------------

    public static KeyPair newEd25519() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("Ed25519");
        return g.generateKeyPair();
    }

    public static byte[] sign(PrivateKey sk, byte[] msg) throws GeneralSecurityException {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(sk);
        s.update(msg);
        return s.sign();
    }

    public static boolean verify(PublicKey pk, byte[] msg, byte[] sig) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(pk);
            s.update(msg);
            return s.verify(sig);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /** Encode an Ed25519 public key as its raw 32-byte little-endian form. */
    public static byte[] ed25519Raw(PublicKey pk) throws GeneralSecurityException {
        EdECPublicKey ed = (EdECPublicKey) pk;
        EdECPoint pt = ed.getPoint();
        byte[] y = pt.getY().toByteArray();     // big-endian
        reverse(y);                             // -> little-endian
        byte[] out = Arrays.copyOf(y, 32);
        if (pt.isXOdd()) out[31] |= (byte) 0x80;
        return out;
    }

    public static PublicKey ed25519FromRaw(byte[] raw32) throws GeneralSecurityException {
        if (raw32.length != 32) throw new InvalidKeyException("Ed25519 raw key must be 32 bytes");
        byte[] le = raw32.clone();
        boolean xOdd = (le[31] & 0x80) != 0;
        le[31] &= 0x7f;
        reverse(le);                            // -> big-endian for BigInteger
        java.math.BigInteger y = new java.math.BigInteger(1, le);
        NamedParameterSpec ns = NamedParameterSpec.ED25519;
        EdECPublicKeySpec spec = new EdECPublicKeySpec(ns, new EdECPoint(xOdd, y));
        return KeyFactory.getInstance("Ed25519").generatePublic(spec);
    }

    // ---- X25519 ephemeral agreement ---------------------------------------

    public static KeyPair newX25519() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("X25519");
        return g.generateKeyPair();
    }

    /** Encode an X25519 public key as its raw 32-byte little-endian u-coordinate. */
    public static byte[] x25519Raw(PublicKey pk) throws GeneralSecurityException {
        XECPublicKey xe = (XECPublicKey) pk;
        byte[] u = xe.getU().toByteArray();     // big-endian
        reverse(u);                             // -> little-endian
        return Arrays.copyOf(u, 32);
    }

    public static PublicKey x25519FromRaw(byte[] raw32) throws GeneralSecurityException {
        if (raw32.length != 32) throw new InvalidKeyException("X25519 raw key must be 32 bytes");
        byte[] le = raw32.clone();
        le[31] &= 0x7f;                         // clear the (unused) high bit
        reverse(le);                            // -> big-endian
        java.math.BigInteger u = new java.math.BigInteger(1, le);
        XECPublicKeySpec spec = new XECPublicKeySpec(NamedParameterSpec.X25519, u);
        return KeyFactory.getInstance("X25519").generatePublic(spec);
    }

    public static byte[] x25519(PrivateKey mine, PublicKey theirs) throws GeneralSecurityException {
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(mine);
        ka.doPhase(theirs, true);
        return ka.generateSecret();             // 32-byte shared secret
    }

    // ---- HKDF-SHA-256 (RFC 5869) ------------------------------------------

    public static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws GeneralSecurityException {
        if (salt == null || salt.length == 0) salt = new byte[32];
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(salt, "HmacSHA256"));
        return m.doFinal(ikm);
    }

    public static byte[] hkdfExpand(byte[] prk, byte[] info, int len) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] out = new byte[len];
        byte[] t = new byte[0];
        int pos = 0, counter = 1;
        while (pos < len) {
            m.reset();
            m.update(t);
            if (info != null) m.update(info);
            m.update((byte) counter);
            t = m.doFinal();
            int n = Math.min(t.length, len - pos);
            System.arraycopy(t, 0, out, pos, n);
            pos += n; counter++;
        }
        return out;
    }

    public static byte[] hkdf(byte[] ikm, byte[] salt, byte[] info, int len) throws GeneralSecurityException {
        return hkdfExpand(hkdfExtract(salt, ikm), info, len);
    }

    /** A keyed PRF (HMAC-SHA-256) used for the region ACK tag (§4.2). */
    public static byte[] prf(byte[] key, byte[]... parts) throws GeneralSecurityException {
        Mac m = Mac.getInstance("HmacSHA256");
        m.init(new SecretKeySpec(key, "HmacSHA256"));
        for (byte[] p : parts) m.update(p);
        return m.doFinal();
    }

    // ---- ChaCha20-Poly1305 AEAD (IETF, 12-byte nonce) ---------------------

    public static byte[] seal(byte[] key, byte[] nonce12, byte[] aad, byte[] plaintext)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
        javax.crypto.spec.IvParameterSpec iv = new javax.crypto.spec.IvParameterSpec(nonce12);
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), iv);
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(plaintext);            // ciphertext || 16-byte tag
    }

    public static byte[] open(byte[] key, byte[] nonce12, byte[] aad, byte[] ciphertext)
            throws GeneralSecurityException {
        Cipher c = Cipher.getInstance("ChaCha20-Poly1305");
        javax.crypto.spec.IvParameterSpec iv = new javax.crypto.spec.IvParameterSpec(nonce12);
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "ChaCha20"), iv);
        if (aad != null) c.updateAAD(aad);
        return c.doFinal(ciphertext);           // throws AEADBadTagException on tamper
    }

    // ---- hashing / random --------------------------------------------------

    public static byte[] sha256(byte[]... parts) throws GeneralSecurityException {
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        for (byte[] p : parts) d.update(p);
        return d.digest();
    }

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RNG.nextBytes(b);
        return b;
    }

    // ---- helpers -----------------------------------------------------------

    private static void reverse(byte[] a) {
        for (int i = 0, j = a.length - 1; i < j; i++, j--) {
            byte t = a[i]; a[i] = a[j]; a[j] = t;
        }
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xf, 16))
                           .append(Character.forDigit(x & 0xf, 16));
        return sb.toString();
    }
}
