package com.larpsmp.moneyevent.auth;

/**
 * Outcome of submitting an email verification code for signup / unverified login.
 */
public sealed interface VerifyEmailResult {

    record Success(Account account) implements VerifyEmailResult {
    }

    record InvalidCode() implements VerifyEmailResult {
    }

    record Expired() implements VerifyEmailResult {
    }

    record AttemptsExhausted() implements VerifyEmailResult {
    }

    record NoChallenge() implements VerifyEmailResult {
    }

    record InternalError() implements VerifyEmailResult {
    }
}
