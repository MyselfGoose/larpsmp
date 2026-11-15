package com.larpsmp.moneyevent.email;

/**
 * Outbound transactional email payload.
 */
public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody
) {
}
