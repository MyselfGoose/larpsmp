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

    public static final String INPUT_IDENTIFIER = "identifier";
    public static final String INPUT_USERNAME = "username";
    public static final String INPUT_EMAIL = "email";
    public static final String INPUT_PASSWORD = "password";

    private AuthDialogKeys() {
    }
}
