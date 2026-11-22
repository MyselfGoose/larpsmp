package com.larpsmp.moneyevent.capture;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

/**
 * Marks players mid-teleport so they do not count for capture advantage until teleport finishes.
 */
public final class CaptureTeleportGuard implements Listener {

    private final Plugin plugin;
    private final BukkitScheduler scheduler;

    public CaptureTeleportGuard(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = plugin.getServer().getScheduler();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        player.setMetadata(
                CaptureEligibility.TELEPORTING_METADATA,
                new FixedMetadataValue(plugin, Boolean.TRUE));
        scheduler.runTask(plugin, () -> player.removeMetadata(CaptureEligibility.TELEPORTING_METADATA, plugin));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // Ensure respawned players are not stuck with a stale teleporting flag.
        event.getPlayer().removeMetadata(CaptureEligibility.TELEPORTING_METADATA, plugin);
    }
}
