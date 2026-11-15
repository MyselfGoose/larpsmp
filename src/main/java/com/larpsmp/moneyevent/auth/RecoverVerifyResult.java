package com.larpsmp.moneyevent.auth;

/**
 * Outcome of verifying a recovery email code (password reset or username recovery).
 */
public sealed interface RecoverVerifyResult {

    record PasswordResetAuthorized(Account account) implements RecoverVerifyResult {
    }

    record UsernameRevealed(String username) implements RecoverVerifyResult {
    }

    record InvalidCode() implements RecoverVerifyResult {
    }

    record Expired() implements RecoverVerifyResult {
    }

    record AttemptsExhausted() implements RecoverVerifyResult {
    }

    record NoChallenge() implements RecoverVerifyResult {
    }

    record InternalError() implements RecoverVerifyResult {
    }
}
