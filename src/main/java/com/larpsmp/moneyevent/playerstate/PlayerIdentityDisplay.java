package com.larpsmp.moneyevent.playerstate;

import net.kyori.adventure.text.Component;

/**
 * Adventure join/quit copy for account-keyed identity.
 *
 * <p>Profile + vanilla command-name binding lives in {@link AccountIdentityBinder}.
 */
public final class PlayerIdentityDisplay {

    private PlayerIdentityDisplay() {
    }

    public static Component joinMessage(String accountUsername) {
        return AccountIdentityBinder.joinMessage(accountUsername);
    }

    public static Component quitMessage(String accountUsername) {
        return AccountIdentityBinder.quitMessage(accountUsername);
    }
}
