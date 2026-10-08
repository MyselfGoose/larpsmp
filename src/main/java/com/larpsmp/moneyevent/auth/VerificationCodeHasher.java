package com.larpsmp.moneyevent.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

/**
 * Generates and hashes short numeric email verification codes.
 */
public final class VerificationCodeHasher {

    private final SecureRandom secureRandom = new SecureRandom();
    private final String pepper;
    private final int codeLength;

    public VerificationCodeHasher(String pepper, int codeLength) {
        this.pepper = Objects.requireNonNull(pepper, "pepper");
        if (codeLength < 4 || codeLength > 10) {
            throw new IllegalArgumentException("codeLength must be between 4 and 10");
        }
        this.codeLength = codeLength;
    }

    public String generateCode() {
        int bound = (int) Math.pow(10, codeLength);
        int value = secureRandom.nextInt(bound);
        return String.format(Locale.ROOT, "%0" + codeLength + "d", value);
    }

    public String hash(String code) {
        Objects.requireNonNull(code, "code");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(pepper.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(code.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public boolean matches(String code, String expectedHash) {
        if (code == null || expectedHash == null) {
            return false;
        }
        String actual = hash(code);
        if (actual.length() != expectedHash.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < actual.length(); i++) {
            diff |= actual.charAt(i) ^ expectedHash.charAt(i);
        }
        return diff == 0;
    }
}
