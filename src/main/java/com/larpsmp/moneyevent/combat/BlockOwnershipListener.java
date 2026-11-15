package com.larpsmp.moneyevent.combat;

import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

/**
 * Records placer ownership for lava, fire, TNT, and trap components.
 */
public final class BlockOwnershipListener implements Listener {

    private final BlockOwnershipTracker ownership;

    public BlockOwnershipListener(BlockOwnershipTracker ownership) {
        this.ownership = ownership;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!OwnedHazardMaterials.isTracked(block.getType())) {
            return;
        }
        Player player = event.getPlayer();
        ownership.setOwner(key(block), player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        ownership.clear(key(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        ownership.clear(key(event.getBlock()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();
        for (Block block : blocks) {
            ownership.clear(key(block));
        }
    }

    /**
     * When TNT ignites, transfer block ownership onto the primed entity via source player if known.
     * Primed TNT attacker resolution still uses {@link TNTPrimed#getSource()} in damage listener.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPrime(ExplosionPrimeEvent event) {
        if (!(event.getEntity() instanceof TNTPrimed primed)) {
            return;
        }
        Block origin = primed.getLocation().getBlock();
        ownership.ownerOf(key(origin)).ifPresent(owner -> {
            // Keep ownership on the block location briefly; damage listener uses entity source first.
            ownership.setOwner(key(origin), owner);
        });
    }

    static BlockKey key(Block block) {
        return BlockKey.of(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
}
