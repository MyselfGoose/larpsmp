package com.larpsmp.moneyevent.auth;

/**
 * Why an email verification code was issued.
 */
public enum EmailChallengePurpose {
    SIGNUP_VERIFY,
    PASSWORD_RESET,
    USERNAME_RECOVERY
}
