package com.larpsmp.moneyevent.display;

import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Read-only access to per-player owned scoreboards used by the money sidebar.
 *
 * <p>Team nametag / tab controllers must write onto these boards and must never call
 * {@code player.setScoreboard} themselves (one sidebar slot per player).
 */
public interface OwnedScoreboardAccess {
    /**
     * Invokes {@code action} for every currently tracked owned scoreboard.
     */
    void forEachOwned(BiConsumer<UUID, Scoreboard> action);

    /**
     * Registers a listener notified when an owned board is created or removed.
     */
    void addOwnedScoreboardListener(OwnedScoreboardListener listener);
}
