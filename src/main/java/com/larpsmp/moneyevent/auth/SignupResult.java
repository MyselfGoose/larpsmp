package com.larpsmp.moneyevent.auth;

/**
 * Outcome of a signup attempt.
 */
public sealed interface SignupResult {

    /**
     * Account created; player must verify email before joining.
     */
    record PendingVerification(Account account, String maskedEmail) implements SignupResult {
    }

    record ValidationError(String message) implements SignupResult {
    }

    record UsernameTaken() implements SignupResult {
    }

    record EmailTaken() implements SignupResult {
    }

    record RateLimited() implements SignupResult {
    }

    record EmailUnavailable() implements SignupResult {
    }

    record InternalError() implements SignupResult {
    }
}
