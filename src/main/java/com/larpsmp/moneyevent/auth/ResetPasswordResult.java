package com.larpsmp.moneyevent.auth;

/**
 * Outcome of setting a new password after recovery code verification.
 */
public sealed interface ResetPasswordResult {

    record Success() implements ResetPasswordResult {
    }

    record ValidationError(String message) implements ResetPasswordResult {
    }

    record Unauthorized() implements ResetPasswordResult {
    }

    record InternalError() implements ResetPasswordResult {
    }
}
