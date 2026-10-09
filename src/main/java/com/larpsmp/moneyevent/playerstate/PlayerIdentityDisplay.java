package com.larpsmp.moneyevent.playerstate;

import com.destroystokyo.paper.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Applies LarpSMP account username to Paper display surfaces.
 *
 * <p>{@link Player#displayName(Component)} / {@link Player#playerListName(Component)} cover chat and
 * tab-list styling. The nametag above the head and many vanilla UI strings still use the GameProfile
 * name, so after auth we replace the player profile with one named for the LarpSMP account (same UUID,
 * preserved skin properties).
 */
public final class PlayerIdentityDisplay {

    private PlayerIdentityDisplay() {
    }

    public static void apply(Player player, String accountUsername) {
        Component name = Component.text(accountUsername, NamedTextColor.WHITE);

        PlayerProfile current = player.getPlayerProfile();
        if (!accountUsername.equals(current.getName())) {
            PlayerProfile renamed = Bukkit.createProfileExact(player.getUniqueId(), accountUsername);
            renamed.setProperties(current.getProperties());
            player.setPlayerProfile(renamed);
        }

        player.displayName(name);
        player.playerListName(name);
        // Legacy setters still consulted by some plugins / older call paths.
        player.setDisplayName(accountUsername);
        player.setPlayerListName(accountUsername);
    }

    public static Component joinMessage(String accountUsername) {
        return Component.text(accountUsername + " joined the game", NamedTextColor.YELLOW);
    }

    public static Component quitMessage(String accountUsername) {
        return Component.text(accountUsername + " left the game", NamedTextColor.YELLOW);
    }
}
