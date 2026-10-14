package no.hyttebok.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Web Push message encryption (RFC 8291, "aes128gcm") and VAPID signing (RFC 8292) with plain JDK crypto.
 * Deliberately free of framework imports so it can be checked on its own.
 */
final class WebPushCrypto {

    static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    static final Base64.Decoder B64_DECODE = Base64.getUrlDecoder();

    private static final int RECORD_SIZE = 4096;
    private static final int PUBLIC_KEY_LENGTH = 65;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ECParameterSpec P256 = ((ECPublicKey) newKeyPair().getPublic()).getParams();

    private WebPushCrypto() {
    }

    static KeyPair newKeyPair() {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Uncompressed point: 0x04 || X || Y, 65 bytes. */
    static byte[] rawPublicKey(ECPublicKey key) {
        return ByteBuffer.allocate(PUBLIC_KEY_LENGTH)
            .put((byte) 4)
            .put(unsigned32(key.getW().getAffineX()))
            .put(unsigned32(key.getW().getAffineY()))
            .array();
    }

    static ECPublicKey publicKey(byte[] raw) throws GeneralSecurityException {
        var x = new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
        var y = new BigInteger(1, Arrays.copyOfRange(raw, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
    }

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        var salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublic, authSecret, newKeyPair(), salt);
    }

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair server, byte[] salt) throws GeneralSecurityException {

        var agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(server.getPrivate());
        agreement.doPhase(publicKey(uaPublic), true);
        var shared = agreement.generateSecret();
        var asPublic = rawPublicKey((ECPublicKey) server.getPublic());

        var ikm = hkdf(authSecret, shared, concat(ascii("WebPush: info\0"), uaPublic, asPublic), 32);
        var cek = hkdf(salt, ikm, ascii("Content-Encoding: aes128gcm\0"), 16);
        var nonce = hkdf(salt, ikm, ascii("Content-Encoding: nonce\0"), 12);

        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // single record: content followed by the 0x02 "last record" delimiter, no padding
        var ciphertext = cipher.doFinal(concat(plaintext, new byte[] {2}));

        var header = ByteBuffer.allocate(16 + 4 + 1 + PUBLIC_KEY_LENGTH)
            .put(salt)
            .putInt(RECORD_SIZE)
            .put((byte) PUBLIC_KEY_LENGTH)
            .put(asPublic)
            .array();

        return concat(header, ciphertext);
    }

    /** VAPID JWT (ES256) proving to the push service that this server sent the message. */
    static String vapidJwt(String audience, String subject, long expiresAtEpochSeconds, PrivateKey key) throws GeneralSecurityException {

        var header = B64.encodeToString(ascii("{\"typ\":\"JWT\",\"alg\":\"ES256\"}"));
        var claims = B64.encodeToString(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresAtEpochSeconds + ",\"sub\":\"" + subject + "\"}")
            .getBytes(StandardCharsets.UTF_8));

        var signer = Signature.getInstance("SHA256withECDSAinP1363Format");
        signer.initSign(key);
        signer.update(ascii(header + "." + claims));

        return header + "." + claims + "." + B64.encodeToString(signer.sign());
    }

    // HKDF (RFC 5869) with a single expand block, enough for outputs up to 32 bytes.
    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {

        var prk = hmac(salt, ikm);
        var okm = hmac(prk, concat(info, new byte[] {1}));

        return Arrays.copyOf(okm, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] unsigned32(BigInteger value) {
        var bytes = value.toByteArray();
        var out = new byte[32];
        var length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, out, 32 - length, length);
        return out;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        var out = ByteBuffer.allocate(Arrays.stream(parts).mapToInt(p -> p.length).sum());
        for (var part : parts) {
            out.put(part);
        }
        return out.array();
    }
}
