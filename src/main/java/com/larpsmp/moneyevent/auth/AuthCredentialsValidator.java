package com.larpsmp.moneyevent.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;

/**
 * Server-side credential checks for the UI-first auth gate.
 */
public final class AuthCredentialsValidator {

    private final TestAccount testAccount;

    public AuthCredentialsValidator(TestAccount testAccount) {
        this.testAccount = testAccount;
    }

    public boolean authenticate(String identifier, String password) {
        String normalizedIdentifier = normalize(identifier);
        if (normalizedIdentifier.isEmpty() || password == null || password.isEmpty()) {
            return false;
        }

        boolean identifierMatches = normalizedIdentifier.equals(normalize(testAccount.username()))
                || normalizedIdentifier.equals(normalize(testAccount.email()));
        if (!identifierMatches) {
            return false;
        }

        return constantTimeEquals(password, testAccount.password());
    }

    /**
     * @return empty when all signup fields are present; otherwise a player-facing error reason
     */
    public Optional<String> validateSignupFields(String username, String email, String password, String emptyFieldsMessage) {
        if (isBlank(username) || isBlank(email) || password == null || password.isEmpty()) {
            return Optional.of(emptyFieldsMessage);
        }
        return Optional.empty();
    }

    public boolean hasEmptyLoginFields(String identifier, String password) {
        return isBlank(identifier) || password == null || password.isEmpty();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }
}
