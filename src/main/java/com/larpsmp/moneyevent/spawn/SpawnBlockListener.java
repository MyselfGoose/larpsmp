package com.larpsmp.moneyevent.spawn;

import java.util.Objects;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Makes blocks inside the configured spawn region unbreakable.
 */
public final class SpawnBlockListener implements Listener {

    private final SpawnService spawnService;

    public SpawnBlockListener(SpawnService spawnService) {
        this.spawnService = Objects.requireNonNull(spawnService, "spawnService");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        SpawnRegion region = spawnService.region();
        if (!region.isDefined()) {
            return;
        }
        Block block = event.getBlock();
        if (region.containsBlock(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ())) {
            event.setCancelled(true);
        }
    }
}
