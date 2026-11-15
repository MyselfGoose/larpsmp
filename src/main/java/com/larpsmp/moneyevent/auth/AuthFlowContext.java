package com.larpsmp.moneyevent.auth;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Multi-step auth UI state held on the configuration-phase session.
 */
public final class AuthFlowContext {

    public enum Screen {
        LOGIN,
        SIGNUP,
        VERIFY_EMAIL,
        FORGOT_HUB,
        FORGOT_EMAIL,
        FORGOT_VERIFY,
        RESET_PASSWORD,
        USERNAME_REVEAL
    }

    private volatile Screen screen = Screen.LOGIN;
    private volatile @Nullable UUID accountId;
    private volatile @Nullable EmailChallengePurpose purpose;
    private volatile @Nullable String maskedEmail;
    private volatile @Nullable String revealedUsername;
    private volatile boolean passwordResetAuthorized;

    public Screen screen() {
        return screen;
    }

    public @Nullable UUID accountId() {
        return accountId;
    }

    public @Nullable EmailChallengePurpose purpose() {
        return purpose;
    }

    public @Nullable String maskedEmail() {
        return maskedEmail;
    }

    public @Nullable String revealedUsername() {
        return revealedUsername;
    }

    public boolean passwordResetAuthorized() {
        return passwordResetAuthorized;
    }

    public void clear() {
        screen = Screen.LOGIN;
        accountId = null;
        purpose = null;
        maskedEmail = null;
        revealedUsername = null;
        passwordResetAuthorized = false;
    }

    public void showLogin() {
        clear();
        screen = Screen.LOGIN;
    }

    public void showSignup() {
        clear();
        screen = Screen.SIGNUP;
    }

    public void beginEmailVerification(UUID accountId, String maskedEmail) {
        this.screen = Screen.VERIFY_EMAIL;
        this.accountId = accountId;
        this.purpose = EmailChallengePurpose.SIGNUP_VERIFY;
        this.maskedEmail = maskedEmail;
        this.revealedUsername = null;
        this.passwordResetAuthorized = false;
    }

    public void showForgotHub() {
        screen = Screen.FORGOT_HUB;
        accountId = null;
        purpose = null;
        maskedEmail = null;
        revealedUsername = null;
        passwordResetAuthorized = false;
    }

    public void beginForgotEmail(EmailChallengePurpose purpose) {
        screen = Screen.FORGOT_EMAIL;
        this.purpose = purpose;
        accountId = null;
        maskedEmail = null;
        revealedUsername = null;
        passwordResetAuthorized = false;
    }

    public void beginForgotVerify(@Nullable UUID accountId, EmailChallengePurpose purpose, String maskedEmail) {
        screen = Screen.FORGOT_VERIFY;
        this.accountId = accountId;
        this.purpose = purpose;
        this.maskedEmail = maskedEmail;
        this.revealedUsername = null;
        this.passwordResetAuthorized = false;
    }

    public void authorizePasswordReset(UUID accountId) {
        screen = Screen.RESET_PASSWORD;
        this.accountId = accountId;
        this.purpose = EmailChallengePurpose.PASSWORD_RESET;
        this.passwordResetAuthorized = true;
        this.revealedUsername = null;
    }

    public void revealUsername(String username) {
        screen = Screen.USERNAME_REVEAL;
        this.revealedUsername = username;
        this.passwordResetAuthorized = false;
    }
}
