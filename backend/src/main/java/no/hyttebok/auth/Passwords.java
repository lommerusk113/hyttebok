package no.hyttebok.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing with the JDK's PBKDF2 (no extra library). Stored as "pbkdf2$iterations$salt$hash" so the
 * iteration count can be raised later without breaking existing passwords.
 */
public final class Passwords {

    public static final int MIN_LENGTH = 6;
    public static final int MAX_LENGTH = 200;
    // OWASP's recommendation for PBKDF2-HMAC-SHA256.
    private static final int ITERATIONS = 600_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {
    }

    public static String hash(String password) {

        var salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        var b64 = Base64.getEncoder();

        return "pbkdf2$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(derive(password, salt, ITERATIONS));
    }

    public static boolean matches(String password, String stored) {

        var parts = stored.split("\\$");
        if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
            return false;
        }
        var b64 = Base64.getDecoder();
        var expected = b64.decode(parts[3]);

        // Constant time, so response times don't leak how much of the hash matched.
        return MessageDigest.isEqual(expected, derive(password, b64.decode(parts[2]), Integer.parseInt(parts[1])));
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS)).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Self-check: {@code java Passwords.java} from this folder. */
    public static void main(String[] args) {
        var stored = hash("riktig-passord");
        if (!matches("riktig-passord", stored) || matches("feil-passord", stored) || matches("x", "garbage") || hash("a").equals(hash("a"))) {
            throw new AssertionError("Passwords self-check failed");
        }
        System.out.println("Passwords ok");
    }
}
