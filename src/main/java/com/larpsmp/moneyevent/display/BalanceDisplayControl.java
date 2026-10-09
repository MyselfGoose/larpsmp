package com.larpsmp.moneyevent.display;

import java.util.UUID;

public interface BalanceDisplayControl extends AutoCloseable {
    void refresh(UUID playerId);

    void playerJoined(UUID playerId);

    void playerQuit(UUID playerId);

    @Override
    void close();
}
