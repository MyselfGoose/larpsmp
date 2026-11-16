package com.larpsmp.moneyevent.capture;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks temporary blocks on capture platforms with DB-backed restore across restarts.
 */
public final class CaptureTempBlockTracker {

    private final JdbcCaptureRepository repository;
    private final Clock clock;
    private final Consumer<String> errorLogger;

    public CaptureTempBlockTracker(
            JdbcCaptureRepository repository,
            Clock clock,
            Consumer<String> errorLogger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public void rememberPlacement(Block block, BlockData priorData, int ttlSeconds) {
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(priorData, "priorData");
        Instant now = clock.instant();
        CaptureTempBlock record = new CaptureTempBlock(
                block.getWorld().getName(),
                block.getX(),
                block.getY(),
                block.getZ(),
                priorData.getAsString(),
                block.getType().name(),
                now,
                now.plusSeconds(Math.max(1, ttlSeconds))
        );
        try {
            repository.upsertTempBlock(record);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to persist capture temp block: " + exception.getMessage());
        }
    }

    public void forget(Block block) {
        try {
            repository.deleteTempBlock(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        } catch (SQLException exception) {
            errorLogger.accept("Failed to delete capture temp block: " + exception.getMessage());
        }
    }

    public boolean isTracked(Block block) {
        try {
            return repository.findTempBlock(
                    block.getWorld().getName(), block.getX(), block.getY(), block.getZ()).isPresent();
        } catch (SQLException exception) {
            errorLogger.accept("Failed to query capture temp blocks: " + exception.getMessage());
            return false;
        }
    }

    /**
     * Restore prior block data for expired temp blocks.
     */
    public void expireDue() {
        Instant now = clock.instant();
        List<CaptureTempBlock> expired;
        try {
            expired = repository.listExpiredTempBlocks(now);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to list expired capture temp blocks: " + exception.getMessage());
            return;
        }
        for (CaptureTempBlock temp : expired) {
            restore(temp);
        }
    }

    /**
     * After restart: re-apply still-valid temp materials and restore expired ones.
     */
    public void restoreAllFromDatabase() {
        List<CaptureTempBlock> blocks;
        try {
            blocks = repository.listTempBlocks();
        } catch (SQLException exception) {
            errorLogger.accept("Failed to load capture temp blocks: " + exception.getMessage());
            return;
        }
        Instant now = clock.instant();
        for (CaptureTempBlock temp : blocks) {
            if (temp.isExpired(now)) {
                restore(temp);
            } else {
                reapplyPlaced(temp);
            }
        }
    }

    private void restore(CaptureTempBlock temp) {
        World world = Bukkit.getWorld(temp.world());
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(temp.x(), temp.y(), temp.z());
        try {
            BlockData prior = Bukkit.createBlockData(temp.priorBlockData());
            block.setBlockData(prior, false);
        } catch (IllegalArgumentException exception) {
            block.setType(Material.AIR, false);
            errorLogger.accept("Invalid prior block data for temp block at "
                    + temp.world() + " " + temp.x() + "," + temp.y() + "," + temp.z()
                    + ": " + exception.getMessage());
        }
        try {
            repository.deleteTempBlock(temp.world(), temp.x(), temp.y(), temp.z());
        } catch (SQLException exception) {
            errorLogger.accept("Failed to delete restored temp block: " + exception.getMessage());
        }
    }

    private void reapplyPlaced(CaptureTempBlock temp) {
        World world = Bukkit.getWorld(temp.world());
        if (world == null) {
            return;
        }
        Material material = materialOrNull(temp.placedMaterial());
        if (material == null || material.isAir()) {
            return;
        }
        Block block = world.getBlockAt(temp.x(), temp.y(), temp.z());
        if (block.getType() != material) {
            block.setType(material, false);
        }
    }

    private static @Nullable Material materialOrNull(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
