package com.larpsmp.moneyevent.display;

import java.io.IOException;
import java.util.UUID;

public interface BalanceDisplayControl extends AutoCloseable {
    boolean isEnabled();

    boolean setEnabled(boolean enabled) throws IOException;

    void refresh(UUID playerId);

    void playerJoined(UUID playerId);

    void playerQuit(UUID playerId);

    @Override
    void close() throws IOException;
}
