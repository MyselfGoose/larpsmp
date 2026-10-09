package com.larpsmp.moneyevent.playerstate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Applies LarpSMP account username to Paper display surfaces.
 */
public final class PlayerIdentityDisplay {

    private PlayerIdentityDisplay() {
    }

    public static void apply(Player player, String accountUsername) {
        Component name = Component.text(accountUsername, NamedTextColor.WHITE);
        player.displayName(name);
        player.playerListName(name);
    }

    public static Component joinMessage(String accountUsername) {
        return Component.text(accountUsername + " joined the game", NamedTextColor.YELLOW);
    }

    public static Component quitMessage(String accountUsername) {
        return Component.text(accountUsername + " left the game", NamedTextColor.YELLOW);
    }
}
