package com.larpsmp.moneyevent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class EnvSettingsTest {

    @Test
    void parsesBasicAssignmentsAndIgnoresComments() {
        Map<String, String> values = EnvSettings.parseDotEnv("""
                # comment
                LARPSMP_DB_USER=larpsmp
                LARPSMP_DB_PASSWORD=secret # trailing comment
                export LARPSMP_JDBC_URL="jdbc:postgresql://127.0.0.1:5433/larpsmp"
                LARPSMP_RESEND_API_KEY=
                INVALID LINE
                =nose
                """);

        assertEquals("larpsmp", values.get("LARPSMP_DB_USER"));
        assertEquals("secret", values.get("LARPSMP_DB_PASSWORD"));
        assertEquals("jdbc:postgresql://127.0.0.1:5433/larpsmp", values.get("LARPSMP_JDBC_URL"));
        assertEquals("", values.get("LARPSMP_RESEND_API_KEY"));
        assertFalse(values.containsKey("INVALID"));
    }

    @Test
    void integrationsDetectConfiguredFlags() {
        IntegrationsConfig unset = new IntegrationsConfig("", "", "", "", "");
        assertFalse(unset.resendConfigured());
        assertFalse(unset.storageConfigured());

        IntegrationsConfig keyOnly = new IntegrationsConfig("re_test", "", "", "", "");
        assertFalse(keyOnly.resendConfigured());

        IntegrationsConfig set = new IntegrationsConfig("re_test", "a@b.co", "pepper", "https://store.example", "key");
        assertTrue(set.resendConfigured());
        assertTrue(set.storageConfigured());
        assertEquals("pepper", set.resolveEmailCodePepper());
    }

    @Test
    void loadDoesNotThrow() {
        EnvSettings env = EnvSettings.load();
        assertEquals("fallback", env.get("LARPSMP_MISSING_KEY_FOR_TEST", "fallback"));
    }
}
