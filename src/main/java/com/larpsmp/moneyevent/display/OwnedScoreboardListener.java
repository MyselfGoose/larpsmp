package com.larpsmp.moneyevent.display;

import java.util.UUID;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Notified when the money sidebar creates or discards a per-player owned scoreboard.
 */
public interface OwnedScoreboardListener {
    void onOwnedBoardReady(UUID playerId, Scoreboard board);

    void onOwnedBoardRemoved(UUID playerId);
}
