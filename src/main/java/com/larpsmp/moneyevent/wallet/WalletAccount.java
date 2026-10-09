package com.larpsmp.moneyevent.wallet;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of an account-linked wallet for commands and HUD.
 */
public record WalletAccount(
        UUID accountId,
        @Nullable UUID minecraftUuid,
        String username,
        long balance) {

    static WalletAccount from(JdbcMoneyRepository.WalletRow row) {
        return new WalletAccount(row.accountId(), row.minecraftUuid(), row.displayName(), row.balance());
    }

    /**
     * Minecraft profile UUID used by commands and notifications. Prefer this over {@link #accountId()}
     * when addressing online/offline players.
     */
    public UUID playerId() {
        if (minecraftUuid == null) {
            throw new IllegalStateException("Wallet for account " + accountId + " has no Minecraft binding");
        }
        return minecraftUuid;
    }

    public String lastKnownUsername() {
        return username;
    }
}
