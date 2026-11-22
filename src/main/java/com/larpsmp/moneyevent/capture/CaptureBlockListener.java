package com.larpsmp.moneyevent.capture;

import java.util.Objects;
import java.util.Optional;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Protects the original capture platform and tracks temporary player-placed blocks.
 *
 * <p>Liquids are allowed (not blocked). Other players may break temporary blocks.
 */
public final class CaptureBlockListener implements Listener {

    private final CaptureService captureService;
    private final CaptureTempBlockTracker tempBlocks;

    public CaptureBlockListener(CaptureService captureService, CaptureTempBlockTracker tempBlocks) {
        this.captureService = Objects.requireNonNull(captureService, "captureService");
        this.tempBlocks = Objects.requireNonNull(tempBlocks, "tempBlocks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<CapturePointDefinition> active = captureService.activePoint();
        if (active.isEmpty() || !active.get().isRegionDefined()) {
            return;
        }
        CapturePointDefinition point = active.get();
        Block block = event.getBlockPlaced();
        int height = captureService.config().heightBlocks();
        if (!sameWorld(point, block)
                || !point.containsVolumeBlock(block.getX(), block.getY(), block.getZ(), height)) {
            return;
        }
        // Platform layer (minY) is unbreakable original; placements above it are temporary.
        if (block.getY() == point.platformY() && isOriginalPlatformCell(point, block)) {
            event.setCancelled(true);
            return;
        }
        BlockData prior = event.getBlockReplacedState().getBlockData();
        int ttl = captureService.config().tempBlockSeconds();
        tempBlocks.rememberPlacement(block, prior, ttl);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Optional<CapturePointDefinition> active = captureService.activePoint();
        if (active.isEmpty() || !active.get().isRegionDefined()) {
            // Still allow cleaning tracked temp blocks if session ended mid-TTL.
            if (tempBlocks.isTracked(event.getBlock())) {
                tempBlocks.forget(event.getBlock());
            }
            return;
        }
        CapturePointDefinition point = active.get();
        Block block = event.getBlock();
        if (!sameWorld(point, block)) {
            return;
        }

        if (tempBlocks.isTracked(block)) {
            // Other players may destroy temporary blocks.
            tempBlocks.forget(block);
            return;
        }

        if (point.containsBlock(block.getX(), block.getY(), block.getZ())
                && block.getY() == point.platformY()) {
            // Original platform is unbreakable.
            event.setCancelled(true);
        }
    }

    private static boolean sameWorld(CapturePointDefinition point, Block block) {
        return point.world() != null && point.world().equalsIgnoreCase(block.getWorld().getName());
    }

    private static boolean isOriginalPlatformCell(CapturePointDefinition point, Block block) {
        return block.getY() == point.platformY()
                && point.containsBlock(block.getX(), block.getY(), block.getZ());
    }
}
