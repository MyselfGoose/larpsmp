package com.larpsmp.moneyevent.auth;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed authentication settings loaded from {@code config.yml}.
 */
public record AuthConfig(
        boolean enabled,
        int timeoutSeconds,
        TestAccount testAccount,
        Messages messages
) {

    public record Messages(
            String loginTitle,
            String loginBody,
            String loginIdentifierLabel,
            String loginPasswordLabel,
            String loginSubmit,
            String loginOpenSignup,
            String loginInvalidCredentials,
            String loginEmptyFields,
            String signupTitle,
            String signupBody,
            String signupUsernameLabel,
            String signupEmailLabel,
            String signupPasswordLabel,
            String signupSubmit,
            String signupBack,
            String signupEmptyFields,
            String signupUnavailableTitle,
            String signupUnavailableBody,
            String signupUnavailableAck,
            String backToMenu,
            String disconnectCancelled,
            String disconnectTimeout,
            String disconnectDenied,
            String disconnectMissingProfile
    ) {
    }

    public static AuthConfig from(FileConfiguration config) {
        int timeoutSeconds = Math.max(5, config.getInt("auth.timeout-seconds", 120));
        TestAccount testAccount = new TestAccount(
                config.getString("auth.test-account.username", "test"),
                config.getString("auth.test-account.email", "test@larpsmp.local"),
                config.getString("auth.test-account.password", "test123")
        );
        Messages messages = new Messages(
                config.getString("auth.messages.login-title", "Login"),
                config.getString("auth.messages.login-body", "Sign in to join LarpSMP."),
                config.getString("auth.messages.login-identifier-label", "Username or email"),
                config.getString("auth.messages.login-password-label", "Password"),
                config.getString("auth.messages.login-submit", "Log in"),
                config.getString("auth.messages.login-open-signup", "Sign up"),
                config.getString("auth.messages.login-invalid-credentials", "Invalid username/email or password."),
                config.getString("auth.messages.login-empty-fields", "Enter your username/email and password."),
                config.getString("auth.messages.signup-title", "Sign up"),
                config.getString("auth.messages.signup-body", "Create an account. All fields are required."),
                config.getString("auth.messages.signup-username-label", "Username"),
                config.getString("auth.messages.signup-email-label", "Email"),
                config.getString("auth.messages.signup-password-label", "Password"),
                config.getString("auth.messages.signup-submit", "Create account"),
                config.getString("auth.messages.signup-back", "Back to login"),
                config.getString("auth.messages.signup-empty-fields", "Username, email, and password are all required."),
                config.getString("auth.messages.signup-unavailable-title", "Registration unavailable"),
                config.getString("auth.messages.signup-unavailable-body", "Account creation is not enabled yet. Use the test login credentials to join."),
                config.getString("auth.messages.signup-unavailable-ack", "Back to login"),
                config.getString("auth.messages.back-to-menu", "Back to main menu"),
                config.getString("auth.messages.disconnect-cancelled", "Returned to the main menu."),
                config.getString("auth.messages.disconnect-timeout", "Authentication timed out. Please reconnect and try again."),
                config.getString("auth.messages.disconnect-denied", "Authentication required to join this server."),
                config.getString("auth.messages.disconnect-missing-profile", "Unable to authenticate: missing player profile.")
        );
        return new AuthConfig(
                config.getBoolean("auth.enabled", true),
                timeoutSeconds,
                testAccount,
                messages
        );
    }
}
