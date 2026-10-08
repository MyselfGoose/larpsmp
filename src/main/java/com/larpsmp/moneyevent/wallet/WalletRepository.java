package com.larpsmp.moneyevent.wallet;

import java.io.IOException;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface WalletRepository extends AutoCloseable {
    Optional<Wallet> load(UUID ownerId) throws IOException;

    List<Wallet> loadAll() throws IOException;

    void save(Wallet wallet) throws IOException;

    default void saveAll(Iterable<Wallet> wallets) throws IOException {
        for (Wallet wallet : wallets) {
            save(wallet);
        }
    }

    @Override
    void close() throws IOException;
}
