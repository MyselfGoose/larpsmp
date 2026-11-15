package com.larpsmp.moneyevent.auth;

import net.kyori.adventure.key.Key;

/**
 * Stable custom-click identifiers for authentication dialogs.
 */
public final class AuthDialogKeys {

    public static final Key LOGIN = Key.key("larpsmp", "auth/login");
    public static final Key OPEN_SIGNUP = Key.key("larpsmp", "auth/open-signup");
    public static final Key SIGNUP = Key.key("larpsmp", "auth/signup");
    public static final Key OPEN_LOGIN = Key.key("larpsmp", "auth/open-login");
    public static final Key OPEN_FORGOT = Key.key("larpsmp", "auth/open-forgot");
    public static final Key FORGOT_CHANGE_PASSWORD = Key.key("larpsmp", "auth/forgot-change-password");
    public static final Key FORGOT_RECOVER_USERNAME = Key.key("larpsmp", "auth/forgot-recover-username");
    public static final Key FORGOT_SEND_CODE = Key.key("larpsmp", "auth/forgot-send-code");
    public static final Key FORGOT_VERIFY = Key.key("larpsmp", "auth/forgot-verify");
    public static final Key FORGOT_RESEND = Key.key("larpsmp", "auth/forgot-resend");
    public static final Key RESET_PASSWORD = Key.key("larpsmp", "auth/reset-password");
    public static final Key VERIFY_EMAIL = Key.key("larpsmp", "auth/verify-email");
    public static final Key VERIFY_RESEND = Key.key("larpsmp", "auth/verify-resend");
    public static final Key BACK_TO_MENU = Key.key("larpsmp", "auth/back-to-menu");

    public static final String INPUT_IDENTIFIER = "identifier";
    public static final String INPUT_USERNAME = "username";
    public static final String INPUT_EMAIL = "email";
    public static final String INPUT_PASSWORD = "password";
    public static final String INPUT_PASSWORD_CONFIRM = "password_confirm";
    public static final String INPUT_CODE = "code";

    private AuthDialogKeys() {
    }
}
