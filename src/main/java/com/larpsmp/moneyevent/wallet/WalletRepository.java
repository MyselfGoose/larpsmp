package com.larpsmp.moneyevent.wallet;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends AutoCloseable {
    Optional<Wallet> load(UUID ownerId) throws IOException;

    void save(Wallet wallet) throws IOException;

    @Override
    void close() throws IOException;
}
