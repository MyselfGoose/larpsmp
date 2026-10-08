package com.larpsmp.moneyevent.auth;

import java.util.UUID;

/**
 * Outcome of requesting a forgot-password / username-recovery code.
 */
public sealed interface ForgotPasswordResult {

    /**
     * Always returned for valid email format when processing completed —
     * does not reveal whether the email is registered.
     */
    record Accepted(UUID accountIdOrNull, String maskedEmailOrBlank, boolean accountFound) implements ForgotPasswordResult {
    }

    record InvalidEmail() implements ForgotPasswordResult {
    }

    record RateLimited() implements ForgotPasswordResult {
    }

    record EmailUnavailable() implements ForgotPasswordResult {
    }

    record Cooldown(long retryAfterSeconds) implements ForgotPasswordResult {
    }

    record InternalError() implements ForgotPasswordResult {
    }
}
