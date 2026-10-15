package no.hyttebok.cabin;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Join codes: 8 characters without look-alikes (no I/L/1, O/0), shown as "ABCD-2345". About 2^39 possible
 * codes, and wrong guesses are limited per user, so they can't be found by trying.
 */
public final class JoinCodes {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private JoinCodes() {
    }

    public static String generate() {
        var code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** What people type: any case, with or without the dash or spaces. */
    public static String normalize(String typed) {
        return typed.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static String display(String code) {
        return code.length() == LENGTH ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }

    /** Self-check: {@code java JoinCodes.java} from this folder. */
    public static void main(String[] args) {
        var code = generate();
        if (code.length() != LENGTH || !normalize(display(code).toLowerCase(Locale.ROOT)).equals(code) || code.matches(".*[ILO01].*")
            || !normalize(" abcd - 2345 ").equals("ABCD2345")) {
            throw new AssertionError("JoinCodes self-check failed: " + code);
        }
        System.out.println("JoinCodes ok " + display(code));
    }
}
