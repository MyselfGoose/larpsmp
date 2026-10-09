package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.config.EnvSettings;
import com.larpsmp.moneyevent.config.IntegrationsConfig;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Typed authentication settings loaded from {@code config.yml}, with secrets
 * overridden by project-root {@code .env} / process environment when present.
 */
public record AuthConfig(
        boolean enabled,
        int timeoutSeconds,
        boolean autoLoginBoundUuid,
        DatabaseConfig database,
        SignupConfig signup,
        RateLimitConfig rateLimit,
        EmailConfig email,
        Messages messages,
        IntegrationsConfig integrations
) {

    public record DatabaseConfig(
            String jdbcUrl,
            String username,
            String password,
            int poolSize
    ) {
    }

    public record SignupConfig(
            boolean enabled,
            int minPasswordLength,
            int maxPasswordLength,
            int minUsernameLength,
            int maxUsernameLength
    ) {
    }

    public record RateLimitConfig(
            int maxAttempts,
            int windowSeconds
    ) {
    }

    public record EmailConfig(
            int codeLength,
            int codeTtlSeconds,
            int resendCooldownSeconds,
            int maxVerifyAttempts
    ) {
    }

    public record Messages(
            String loginTitle,
            String loginBody,
            String loginIdentifierLabel,
            String loginPasswordLabel,
            String loginSubmit,
            String loginOpenSignup,
            String loginForgotPassword,
            String loginInvalidCredentials,
            String loginEmptyFields,
            String loginUuidBoundOther,
            String loginAccountBoundOther,
            String loginRateLimited,
            String loginInternalError,
            String loginEmailNotVerified,
            String signupTitle,
            String signupBody,
            String signupUsernameLabel,
            String signupEmailLabel,
            String signupPasswordLabel,
            String signupSubmit,
            String signupBack,
            String signupEmptyFields,
            String signupInvalidUsername,
            String signupInvalidEmail,
            String signupInvalidPassword,
            String signupUsernameTaken,
            String signupEmailTaken,
            String signupDisabled,
            String signupRateLimited,
            String signupInternalError,
            String signupEmailUnavailable,
            String verifyTitle,
            String verifyBody,
            String verifyCodeLabel,
            String verifySubmit,
            String verifyResend,
            String verifyBack,
            String verifyInvalidCode,
            String verifyExpired,
            String verifyAttemptsExhausted,
            String verifyEmptyCode,
            String verifyResent,
            String verifyCooldown,
            String verifyInternalError,
            String forgotHubTitle,
            String forgotHubBody,
            String forgotChangePassword,
            String forgotRecoverUsername,
            String forgotBack,
            String forgotEmailTitle,
            String forgotEmailBody,
            String forgotEmailLabel,
            String forgotEmailSubmit,
            String forgotEmailSent,
            String forgotEmailInvalid,
            String forgotEmailUnavailable,
            String forgotEmailInternalError,
            String resetPasswordTitle,
            String resetPasswordBody,
            String resetPasswordLabel,
            String resetPasswordConfirmLabel,
            String resetPasswordSubmit,
            String resetPasswordSuccess,
            String resetPasswordMismatch,
            String resetPasswordInvalid,
            String resetPasswordInternalError,
            String usernameRevealTitle,
            String usernameRevealBody,
            String usernameRevealBack,
            String uuidAlreadyBound,
            String backToMenu,
            String disconnectCancelled,
            String disconnectTimeout,
            String disconnectDenied,
            String disconnectMissingProfile,
            String disconnectDatabaseUnavailable
    ) {
    }

    public static AuthConfig from(FileConfiguration config) {
        return from(config, EnvSettings.load());
    }

    public static AuthConfig from(FileConfiguration config, EnvSettings env) {
        int timeoutSeconds = Math.max(5, config.getInt("auth.timeout-seconds", 600));

        DatabaseConfig database = new DatabaseConfig(
                env.get(
                        EnvSettings.JDBC_URL,
                        config.getString("auth.database.jdbc-url", "jdbc:postgresql://127.0.0.1:5433/larpsmp")
                ),
                env.get(
                        EnvSettings.DB_USER,
                        config.getString("auth.database.username", "larpsmp")
                ),
                env.get(
                        EnvSettings.DB_PASSWORD,
                        config.getString("auth.database.password", "larpsmp")
                ),
                Math.max(1, env.getInt(
                        EnvSettings.DB_POOL_SIZE,
                        config.getInt("auth.database.pool-size", 5)
                ))
        );

        SignupConfig signup = new SignupConfig(
                config.getBoolean("auth.signup.enabled", true),
                Math.max(1, config.getInt("auth.signup.min-password-length", 8)),
                Math.max(1, config.getInt("auth.signup.max-password-length", 64)),
                Math.max(1, config.getInt("auth.signup.min-username-length", 3)),
                Math.max(1, config.getInt("auth.signup.max-username-length", 16))
        );

        RateLimitConfig rateLimit = new RateLimitConfig(
                Math.max(1, config.getInt("auth.rate-limit.max-attempts", 5)),
                Math.max(1, config.getInt("auth.rate-limit.window-seconds", 300))
        );

        EmailConfig email = new EmailConfig(
                Math.clamp(config.getInt("auth.email.code-length", 6), 4, 10),
                Math.max(60, config.getInt("auth.email.code-ttl-seconds", 600)),
                Math.max(10, config.getInt("auth.email.resend-cooldown-seconds", 60)),
                Math.max(1, config.getInt("auth.email.max-verify-attempts", 5))
        );

        Messages messages = loadMessages(config);

        return new AuthConfig(
                config.getBoolean("auth.enabled", true),
                timeoutSeconds,
                config.getBoolean("auth.auto-login-bound-uuid", false),
                database,
                signup,
                rateLimit,
                email,
                messages,
                IntegrationsConfig.from(env)
        );
    }

    private static Messages loadMessages(FileConfiguration config) {
        return new Messages(
                config.getString("auth.messages.login-title", "Login"),
                config.getString("auth.messages.login-body", "Sign in to join LarpSMP."),
                config.getString("auth.messages.login-identifier-label", "Username or email"),
                config.getString("auth.messages.login-password-label", "Password"),
                config.getString("auth.messages.login-submit", "Log in"),
                config.getString("auth.messages.login-open-signup", "Sign up"),
                config.getString("auth.messages.login-forgot-password", "Forgot password"),
                config.getString("auth.messages.login-invalid-credentials", "Invalid username/email or password."),
                config.getString("auth.messages.login-empty-fields", "Enter your username/email and password."),
                config.getString("auth.messages.login-uuid-bound-other",
                        "This Minecraft profile is already linked to another account."),
                config.getString("auth.messages.login-account-bound-other",
                        "This account is linked to a different Minecraft profile."),
                config.getString("auth.messages.login-rate-limited",
                        "Too many failed attempts. Please wait and try again."),
                config.getString("auth.messages.login-internal-error",
                        "Authentication is temporarily unavailable. Please try again later."),
                config.getString("auth.messages.login-email-not-verified",
                        "Verify your email before joining. Enter the code we sent you."),
                config.getString("auth.messages.signup-title", "Sign up"),
                config.getString("auth.messages.signup-body",
                        "Create an account. We will email a verification code before you can join."),
                config.getString("auth.messages.signup-username-label", "Username"),
                config.getString("auth.messages.signup-email-label", "Email"),
                config.getString("auth.messages.signup-password-label", "Password"),
                config.getString("auth.messages.signup-submit", "Create account"),
                config.getString("auth.messages.signup-back", "Back to login"),
                config.getString("auth.messages.signup-empty-fields", "Username, email, and password are all required."),
                config.getString("auth.messages.signup-invalid-username",
                        "Username must be 3-16 characters: letters, numbers, or underscore."),
                config.getString("auth.messages.signup-invalid-email", "Enter a valid email address."),
                config.getString("auth.messages.signup-invalid-password",
                        "Password must be between 8 and 64 characters."),
                config.getString("auth.messages.signup-username-taken", "That username is already taken."),
                config.getString("auth.messages.signup-email-taken", "That email is already registered."),
                config.getString("auth.messages.signup-disabled", "Account creation is currently disabled."),
                config.getString("auth.messages.signup-rate-limited",
                        "Too many failed attempts. Please wait and try again."),
                config.getString("auth.messages.signup-internal-error",
                        "Account creation is temporarily unavailable. Please try again later."),
                config.getString("auth.messages.signup-email-unavailable",
                        "Email verification is unavailable. Please try again later."),
                config.getString("auth.messages.verify-title", "Verify email"),
                config.getString("auth.messages.verify-body",
                        "Enter the 6-digit code sent to {email}."),
                config.getString("auth.messages.verify-code-label", "Verification code"),
                config.getString("auth.messages.verify-submit", "Verify"),
                config.getString("auth.messages.verify-resend", "Resend code"),
                config.getString("auth.messages.verify-back", "Back"),
                config.getString("auth.messages.verify-invalid-code", "That code is incorrect. Try again."),
                config.getString("auth.messages.verify-expired", "That code expired. Request a new one."),
                config.getString("auth.messages.verify-attempts-exhausted",
                        "Too many incorrect codes. Request a new one."),
                config.getString("auth.messages.verify-empty-code", "Enter the verification code from your email."),
                config.getString("auth.messages.verify-resent", "A new code was sent to {email}."),
                config.getString("auth.messages.verify-cooldown",
                        "Please wait {seconds}s before requesting another code."),
                config.getString("auth.messages.verify-internal-error",
                        "Verification is temporarily unavailable. Please try again later."),
                config.getString("auth.messages.forgot-hub-title", "Account recovery"),
                config.getString("auth.messages.forgot-hub-body",
                        "Choose how you want to recover access. We will email a verification code."),
                config.getString("auth.messages.forgot-change-password", "Change password"),
                config.getString("auth.messages.forgot-recover-username", "Find username"),
                config.getString("auth.messages.forgot-back", "Back to login"),
                config.getString("auth.messages.forgot-email-title", "Recovery email"),
                config.getString("auth.messages.forgot-email-body",
                        "Enter the email on your account. If it matches, we will send a code."),
                config.getString("auth.messages.forgot-email-label", "Email"),
                config.getString("auth.messages.forgot-email-submit", "Send code"),
                config.getString("auth.messages.forgot-email-sent",
                        "If an account exists for that email, a code was sent."),
                config.getString("auth.messages.forgot-email-invalid", "Enter a valid email address."),
                config.getString("auth.messages.forgot-email-unavailable",
                        "Email recovery is unavailable. Please try again later."),
                config.getString("auth.messages.forgot-email-internal-error",
                        "Recovery is temporarily unavailable. Please try again later."),
                config.getString("auth.messages.reset-password-title", "New password"),
                config.getString("auth.messages.reset-password-body", "Choose a new password for your account."),
                config.getString("auth.messages.reset-password-label", "New password"),
                config.getString("auth.messages.reset-password-confirm-label", "Confirm password"),
                config.getString("auth.messages.reset-password-submit", "Update password"),
                config.getString("auth.messages.reset-password-success",
                        "Password updated. Log in with your new password."),
                config.getString("auth.messages.reset-password-mismatch", "Passwords do not match."),
                config.getString("auth.messages.reset-password-invalid",
                        "Password must be between 8 and 64 characters."),
                config.getString("auth.messages.reset-password-internal-error",
                        "Password update failed. Please try again later."),
                config.getString("auth.messages.username-reveal-title", "Your username"),
                config.getString("auth.messages.username-reveal-body",
                        "The username for this email is: {username}"),
                config.getString("auth.messages.username-reveal-back", "Back to login"),
                config.getString("auth.messages.uuid-already-bound",
                        "Could not finish signup because this Minecraft profile changed mid-request. Please try again."),
                config.getString("auth.messages.back-to-menu", "Back to main menu"),
                config.getString("auth.messages.disconnect-cancelled", "Returned to the main menu."),
                config.getString("auth.messages.disconnect-timeout",
                        "Authentication timed out. Please reconnect and try again."),
                config.getString("auth.messages.disconnect-denied", "Authentication required to join this server."),
                config.getString("auth.messages.disconnect-missing-profile",
                        "Unable to authenticate: missing player profile."),
                config.getString("auth.messages.disconnect-database-unavailable",
                        "Authentication is unavailable. Please try again later.")
        );
    }
}
