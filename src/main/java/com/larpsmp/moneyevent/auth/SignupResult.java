package com.larpsmp.moneyevent.auth;

/**
 * Outcome of a signup attempt.
 */
public sealed interface SignupResult {

    record Success(Account account) implements SignupResult {
    }

    record ValidationError(String message) implements SignupResult {
    }

    record UsernameTaken() implements SignupResult {
    }

    record EmailTaken() implements SignupResult {
    }

    record RateLimited() implements SignupResult {
    }

    record InternalError() implements SignupResult {
    }
}
