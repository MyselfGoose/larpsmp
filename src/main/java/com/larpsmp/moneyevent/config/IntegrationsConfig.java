package com.larpsmp.moneyevent.config;

/**
 * Third-party API settings sourced from {@code .env} / process environment.
 * Empty strings mean the integration is not configured yet.
 */
public record IntegrationsConfig(
        String resendApiKey,
        String resendFromEmail,
        String storageBaseUrl,
        String storageApiKey
) {

    public static IntegrationsConfig from(EnvSettings env) {
        return new IntegrationsConfig(
                env.get(EnvSettings.RESEND_API_KEY, ""),
                env.get(EnvSettings.RESEND_FROM_EMAIL, ""),
                env.get(EnvSettings.STORAGE_BASE_URL, ""),
                env.get(EnvSettings.STORAGE_API_KEY, "")
        );
    }

    public boolean resendConfigured() {
        return !resendApiKey.isBlank();
    }

    public boolean storageConfigured() {
        return !storageBaseUrl.isBlank() && !storageApiKey.isBlank();
    }
}
