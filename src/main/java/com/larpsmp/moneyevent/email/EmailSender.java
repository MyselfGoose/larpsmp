package com.larpsmp.moneyevent.email;

/**
 * Sends transactional email. Implementations must never log plaintext codes or API secrets.
 */
public interface EmailSender {

    void send(EmailMessage message) throws EmailSendException;
}
