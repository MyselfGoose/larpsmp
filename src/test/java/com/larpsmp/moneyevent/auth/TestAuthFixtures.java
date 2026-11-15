package com.larpsmp.moneyevent.auth;

final class TestAuthFixtures {

    private TestAuthFixtures() {
    }

    static AuthConfig.Messages sampleMessages() {
        return new AuthConfig.Messages(
                "Login", "body", "id", "pw", "Log in", "Sign up", "Forgot",
                "invalid", "empty login", "uuid other", "account other", "rate login", "internal login",
                "email not verified", "already online",
                "Sign up", "signup body", "user", "email", "pw", "Create", "Back",
                "empty signup", "bad user", "bad email", "bad password",
                "user taken", "email taken", "disabled", "rate signup", "internal signup", "email unavailable",
                "Verify", "verify body {email}", "code", "Verify", "Resend", "Back",
                "bad code", "expired", "exhausted", "empty code", "resent {email}", "cooldown {seconds}",
                "verify internal",
                "Forgot hub", "forgot body", "Change pw", "Find user", "Back",
                "Forgot email", "forgot email body", "Email", "Send",
                "sent", "bad email", "email unavailable", "forgot internal",
                "Reset", "reset body", "New pw", "Confirm", "Update",
                "reset ok", "mismatch", "bad pw", "reset internal",
                "Username", "username is {username}", "Back login",
                "uuid bound", "Back to menu", "cancelled", "timeout", "denied", "missing", "db down"
        );
    }

    static AuthConfig.EmailConfig sampleEmailConfig() {
        return new AuthConfig.EmailConfig(6, 600, 1, 5);
    }
}
