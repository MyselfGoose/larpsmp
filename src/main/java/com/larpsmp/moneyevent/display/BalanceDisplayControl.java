package com.larpsmp.moneyevent.display;

import java.util.UUID;

public interface BalanceDisplayControl extends AutoCloseable {
    void refresh(UUID playerId);

    /**
     * Refreshes the HUD for every online member of the given team (team wealth fan-out).
     */
    void refreshTeam(UUID teamId);

    /**
     * Refreshes the player and all online teammates after a personal balance change.
     */
    void refreshPlayerAndTeammates(UUID playerId);

    void playerJoined(UUID playerId);

    void playerQuit(UUID playerId);

    @Override
    void close();
}
