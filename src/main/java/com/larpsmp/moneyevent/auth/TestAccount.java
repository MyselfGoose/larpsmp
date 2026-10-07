package com.larpsmp.moneyevent.auth;

/**
 * Configured test identity used while real registration is not implemented.
 */
public record TestAccount(String username, String email, String password) {

    public TestAccount {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("test account username must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("test account email must not be blank");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("test account password must not be empty");
        }
        username = username.trim();
        email = email.trim();
    }
}
