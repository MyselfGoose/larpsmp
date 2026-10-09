package com.larpsmp.moneyevent.playerstate;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Applies and persists account-keyed bodies around join/quit.
 */
public final class PlayerStateListener implements Listener {

    private final Plugin plugin;
    private final AccountSessionManager sessions;
    private final PlayerStateService playerStates;
    private final Logger logger;

    public PlayerStateListener(
            Plugin plugin,
            AccountSessionManager sessions,
            PlayerStateService playerStates,
            Logger logger
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.playerStates = playerStates;
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        UUID profileId = event.getConnection().getProfile().getId();
        if (profileId == null) {
            return;
        }
        AccountSession session = sessions.findByMinecraftUuid(profileId).orElse(null);
        if (session == null || session.preloadedState() == null) {
            return;
        }
        Location location = playerStates.resolveLocation(session.preloadedState());
        if (location != null && location.getWorld() != null) {
            event.setSpawnLocation(location);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        AccountSession session = sessions.findByMinecraftUuid(player.getUniqueId()).orElse(null);
        if (session == null) {
            logger.warning("Player joined without account session: " + player.getUniqueId()
                    + " (" + player.getName() + ")");
            return;
        }

        AccountPlayerState state = session.preloadedState();
        if (state == null) {
            logger.severe("Account session for '" + session.username()
                    + "' has no preloaded state; disconnecting.");
            player.kick(Component.text("Failed to load account body. Please reconnect.", NamedTextColor.RED));
            sessions.endByMinecraftUuid(player.getUniqueId());
            return;
        }

        try {
            if (session.markApplied()) {
                playerStates.apply(player, state);
            }
            PlayerIdentityDisplay.apply(player, session.username());
            // Re-apply next tick so later join handlers cannot leave the client name visible.
            String accountUsername = session.username();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                sessions.findByMinecraftUuid(player.getUniqueId()).ifPresent(still -> {
                    if (still.accountId().equals(session.accountId())) {
                        PlayerIdentityDisplay.apply(player, accountUsername);
                    }
                });
            });
            event.joinMessage(PlayerIdentityDisplay.joinMessage(session.username()));
            sessions.activate(player.getUniqueId());
            logger.info("Applied account body for '" + session.username()
                    + "' (minecraftUuid=" + player.getUniqueId() + ")");
        } catch (Exception exception) {
            logger.log(Level.SEVERE, "Failed to apply account body for '" + session.username() + "'", exception);
            player.kick(Component.text("Failed to load account body. Please reconnect.", NamedTextColor.RED));
            sessions.endByMinecraftUuid(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        AccountSession session = sessions.findByMinecraftUuid(player.getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }

        event.quitMessage(PlayerIdentityDisplay.quitMessage(session.username()));

        boolean saved = playerStates.savePlayer(player, session.accountId());
        if (saved) {
            playerStates.clearVanillaShell(player);
            logger.info("Saved account body for '" + session.username()
                    + "' (minecraftUuid=" + player.getUniqueId() + ")");
        } else {
            logger.severe("Quit save failed for '" + session.username()
                    + "'; vanilla playerdata left intact as emergency backup.");
        }
        sessions.endByMinecraftUuid(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent event) {
        AccountSession session = sessions.findByMinecraftUuid(event.getEntity().getUniqueId()).orElse(null);
        if (session == null) {
            return;
        }
        Component death = event.deathMessage();
        if (death == null) {
            return;
        }
        String clientName = event.getEntity().getName();
        if (clientName == null || clientName.isBlank() || clientName.equalsIgnoreCase(session.username())) {
            return;
        }
        event.deathMessage(death.replaceText(builder -> builder
                .matchLiteral(clientName)
                .replacement(session.username())));
    }
}
