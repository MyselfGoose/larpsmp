package com.larpsmp.moneyevent.email;

/**
 * Failure while delivering transactional email.
 */
public final class EmailSendException extends Exception {

    public EmailSendException(String message) {
        super(message);
    }

    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
