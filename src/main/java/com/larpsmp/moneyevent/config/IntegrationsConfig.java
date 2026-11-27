package com.larpsmp.moneyevent.config;

/**
 * Third-party API settings sourced from {@code .env} / process environment.
 * Empty strings mean the integration is not configured yet.
 */
public record IntegrationsConfig(
        String resendApiKey,
        String resendFromEmail,
        String emailCodePepper,
        String storageBaseUrl,
        String storageApiKey
) {

    public static IntegrationsConfig from(EnvSettings env) {
        return new IntegrationsConfig(
                env.get(EnvSettings.RESEND_API_KEY, ""),
                env.get(EnvSettings.RESEND_FROM_EMAIL, ""),
                env.get(EnvSettings.EMAIL_CODE_PEPPER, ""),
                env.get(EnvSettings.STORAGE_BASE_URL, ""),
                env.get(EnvSettings.STORAGE_API_KEY, "")
        );
    }

    public boolean resendConfigured() {
        return !resendApiKey.isBlank() && !resendFromEmail.isBlank();
    }

    public boolean storageConfigured() {
        return !storageBaseUrl.isBlank() && !storageApiKey.isBlank();
    }

    public static final String DEV_EMAIL_CODE_PEPPER = "larpsmp-dev-email-code-pepper";

    /**
     * Pepper used when hashing email verification codes. Falls back to a local-dev default when
     * neither {@code LARPSMP_EMAIL_CODE_PEPPER} nor Resend is configured.
     *
     * <p>When Resend is configured, callers must require an explicit pepper
     * ({@link #hasExplicitEmailCodePepper()}) instead of deriving one from the API key.
     */
    public String resolveEmailCodePepper() {
        if (!emailCodePepper.isBlank()) {
            return emailCodePepper;
        }
        return DEV_EMAIL_CODE_PEPPER;
    }

    /** True when {@code LARPSMP_EMAIL_CODE_PEPPER} is set to a non-blank value. */
    public boolean hasExplicitEmailCodePepper() {
        return !emailCodePepper.isBlank();
    }

    /**
     * Production-safe: Resend delivery requires an explicit email-code pepper so codes are not
     * hashed with a public default or a value derived from the API key.
     */
    public boolean emailChallengesReady() {
        return resendConfigured() && hasExplicitEmailCodePepper();
    }
}
