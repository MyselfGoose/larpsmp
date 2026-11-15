package com.larpsmp.moneyevent.email;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.larpsmp.moneyevent.auth.EmailChallengePurpose;
import org.junit.jupiter.api.Test;

final class EmailTemplatesTest {

    @Test
    void buildsHtmlAndTextContainingCode() {
        EmailMessage message = EmailTemplates.verificationCode(
                "player@example.com",
                EmailChallengePurpose.SIGNUP_VERIFY,
                "482913",
                10
        );
        assertTrue(message.subject().toLowerCase().contains("confirm"));
        assertTrue(message.htmlBody().contains("482913"));
        assertTrue(message.textBody().contains("482913"));
        assertTrue(message.htmlBody().contains("LarpSMP"));
        assertFalse(message.htmlBody().contains("<script"));
    }

    @Test
    void masksEmailLocalPart() {
        assertTrue(EmailTemplates.maskEmail("alex@example.com").contains("@example.com"));
        assertTrue(EmailTemplates.maskEmail("alex@example.com").startsWith("a"));
        assertFalse(EmailTemplates.maskEmail("alex@example.com").equals("alex@example.com"));
    }
}
