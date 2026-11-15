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

    /**
     * Pepper used when hashing email verification codes. Falls back to a stable
     * value derived from the Resend key when present, otherwise a local-dev default.
     */
    public String resolveEmailCodePepper() {
        if (!emailCodePepper.isBlank()) {
            return emailCodePepper;
        }
        if (!resendApiKey.isBlank()) {
            return "resend:" + resendApiKey;
        }
        return "larpsmp-dev-email-code-pepper";
    }
}
