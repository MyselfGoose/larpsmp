package com.larpsmp.moneyevent.auth;

/**
 * Outcome of a login attempt.
 */
public sealed interface LoginResult {

    record Success(Account account) implements LoginResult {
    }

    record InvalidCredentials() implements LoginResult {
    }

    record UuidBoundToOtherAccount() implements LoginResult {
    }

    record AccountBoundToOtherUuid() implements LoginResult {
    }

    record RateLimited() implements LoginResult {
    }

    record InternalError() implements LoginResult {
    }
}
