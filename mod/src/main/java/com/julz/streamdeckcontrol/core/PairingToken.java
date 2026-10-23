package com.julz.streamdeckcontrol.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;

/** Pairing codes such as {@code K7QM-2XPA}: short enough to type, long enough not to guess. */
public final class PairingToken {
    // No 0/O or 1/I, so the code can be read off the screen without mistakes.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PairingToken() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 8; i++) {
            if (i == 4) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Ignores case, spaces and dashes, so "k7qm 2xpa" matches "K7QM-2XPA". */
    public static String normalize(String token) {
        if (token == null) {
            return "";
        }
        return token.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    public static boolean matches(String expected, String given) {
        byte[] a = normalize(expected).getBytes(StandardCharsets.UTF_8);
        byte[] b = normalize(given).getBytes(StandardCharsets.UTF_8);
        return a.length > 0 && MessageDigest.isEqual(a, b);
    }
}
