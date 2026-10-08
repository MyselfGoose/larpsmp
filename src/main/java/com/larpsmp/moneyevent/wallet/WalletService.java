package com.larpsmp.moneyevent.wallet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WalletService implements AutoCloseable {
    public static final long STARTING_BALANCE = 200;

    private final WalletRepository repository;
    private final Map<UUID, Wallet> wallets = new HashMap<>();
    private boolean closed;

    public WalletService(WalletRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public synchronized Optional<Wallet> find(UUID ownerId) throws IOException {
        ensureOpen();
        Wallet cached = wallets.get(ownerId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Wallet> loaded = repository.load(ownerId);
        loaded.ifPresent(wallet -> wallets.put(ownerId, wallet));
        return loaded;
    }

    public synchronized Wallet getOrCreate(UUID ownerId, String lastKnownUsername) throws IOException {
        ensureOpen();
        Optional<Wallet> existing = find(ownerId);
        if (existing.isEmpty()) {
            Wallet created = new Wallet(ownerId, 0, lastKnownUsername, false);
            repository.save(created);
            wallets.put(ownerId, created);
            return created;
        }

        Wallet wallet = existing.orElseThrow();
        if (!wallet.lastKnownUsername().equals(lastKnownUsername)) {
            String previousUsername = wallet.lastKnownUsername();
            wallet.setLastKnownUsername(lastKnownUsername);
            try {
                repository.save(wallet);
            } catch (IOException exception) {
                wallet.restore(wallet.balance(), previousUsername, wallet.startingBalanceGranted());
                throw exception;
            }
        }
        return wallet;
    }

    /**
     * Integration point for the future authentication/roster system. Call this only after that
     * system has confirmed that the UUID belongs to an eligible competitor.
     */
    public synchronized Wallet initializeEligibleCompetitor(UUID ownerId, String lastKnownUsername)
            throws IOException {
        Wallet wallet = getOrCreate(ownerId, lastKnownUsername);
        if (!wallet.startingBalanceGranted()) {
            long previousBalance = wallet.balance();
            wallet.grantStartingBalance(STARTING_BALANCE);
            try {
                repository.save(wallet);
            } catch (IOException exception) {
                wallet.restore(previousBalance, wallet.lastKnownUsername(), false);
                throw exception;
            }
        }
        return wallet;
    }

    public synchronized void setBalance(UUID ownerId, long balance) throws IOException {
        Wallet wallet = find(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("No wallet exists for " + ownerId));
        long previous = wallet.balance();
        wallet.setBalance(balance);
        try {
            repository.save(wallet);
        } catch (IOException exception) {
            wallet.setBalance(previous);
            throw exception;
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            closed = true;
            wallets.clear();
            repository.close();
        }
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Wallet service is closed");
        }
    }
}
