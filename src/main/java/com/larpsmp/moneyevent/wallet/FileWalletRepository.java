package com.larpsmp.moneyevent.wallet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

public final class FileWalletRepository implements WalletRepository {
    private static final String SCHEMA_VERSION = "1";
    private final Path directory;
    private boolean closed;

    public FileWalletRepository(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        if (!Files.isDirectory(this.directory)) {
            throw new IOException("Wallet storage path is not a directory: " + this.directory);
        }
    }

    @Override
    public synchronized Optional<Wallet> load(UUID ownerId) throws IOException {
        ensureOpen();
        Path file = walletFile(ownerId);
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
        } catch (IllegalArgumentException exception) {
            throw corrupt(file, "invalid properties encoding", exception);
        }

        String version = required(values, "schemaVersion", file);
        if (!SCHEMA_VERSION.equals(version)) {
            throw corrupt(file, "unsupported schema version: " + version, null);
        }

        UUID storedId;
        long balance;
        try {
            storedId = UUID.fromString(required(values, "ownerId", file));
            balance = Long.parseLong(required(values, "balance", file));
        } catch (IllegalArgumentException exception) {
            throw corrupt(file, "invalid UUID or balance", exception);
        }
        if (!ownerId.equals(storedId)) {
            throw corrupt(file, "owner UUID does not match filename", null);
        }
        if (balance < 0) {
            throw corrupt(file, "negative balance", null);
        }

        String grantedValue = required(values, "startingBalanceGranted", file);
        if (!grantedValue.equals("true") && !grantedValue.equals("false")) {
            throw corrupt(file, "invalid startingBalanceGranted value", null);
        }

        try {
            return Optional.of(new Wallet(
                    storedId,
                    balance,
                    required(values, "lastKnownUsername", file),
                    Boolean.parseBoolean(grantedValue)));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw corrupt(file, "invalid wallet fields", exception);
        }
    }

    @Override
    public synchronized List<Wallet> loadAll() throws IOException {
        ensureOpen();
        List<Wallet> wallets = new ArrayList<>();
        try (var paths = Files.list(directory)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".properties")).toList()) {
                String filename = path.getFileName().toString();
                UUID ownerId;
                try {
                    ownerId = UUID.fromString(filename.substring(0, filename.length() - ".properties".length()));
                } catch (IllegalArgumentException exception) {
                    throw corrupt(path, "invalid wallet filename", exception);
                }
                wallets.add(load(ownerId).orElseThrow());
            }
        }
        return List.copyOf(wallets);
    }

    @Override
    public synchronized void save(Wallet wallet) throws IOException {
        ensureOpen();
        if (wallet.balance() < 0) {
            throw new IllegalArgumentException("Wallet balance cannot be negative");
        }

        Properties values = new Properties();
        values.setProperty("schemaVersion", SCHEMA_VERSION);
        values.setProperty("ownerId", wallet.ownerId().toString());
        values.setProperty("balance", Long.toString(wallet.balance()));
        values.setProperty("lastKnownUsername", wallet.lastKnownUsername());
        values.setProperty("startingBalanceGranted", Boolean.toString(wallet.startingBalanceGranted()));

        Path destination = walletFile(wallet.ownerId());
        Path temporary = Files.createTempFile(directory, wallet.ownerId() + "-", ".tmp");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "LarpSMP wallet data");
            }
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    private Path walletFile(UUID ownerId) {
        return directory.resolve(ownerId + ".properties");
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Wallet repository is closed");
        }
    }

    private static String required(Properties values, String key, Path file) throws WalletStorageException {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) {
            throw corrupt(file, "missing or blank " + key, null);
        }
        return value;
    }

    private static WalletStorageException corrupt(Path file, String detail, Throwable cause) {
        String message = "Malformed wallet data in " + file + ": " + detail;
        return cause == null ? new WalletStorageException(message) : new WalletStorageException(message, cause);
    }
}
