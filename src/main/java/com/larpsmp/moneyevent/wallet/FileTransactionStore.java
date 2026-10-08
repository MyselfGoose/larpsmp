package com.larpsmp.moneyevent.wallet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class FileTransactionStore implements AutoCloseable {
    private static final String SCHEMA_VERSION = "1";
    private final WalletRepository walletRepository;
    private final Path directory;
    private final Path recordsDirectory;
    private final Path journalFile;
    private final BiConsumer<JournalCheckpoint, Integer> checkpoint;
    private boolean closed;

    public FileTransactionStore(WalletRepository walletRepository, Path directory) throws IOException {
        this(walletRepository, directory, (ignoredCheckpoint, ignoredWriteCount) -> { });
    }

    FileTransactionStore(
            WalletRepository walletRepository,
            Path directory,
            BiConsumer<JournalCheckpoint, Integer> checkpoint) throws IOException {
        this.walletRepository = walletRepository;
        this.directory = directory.toAbsolutePath().normalize();
        this.recordsDirectory = this.directory.resolve("records");
        this.journalFile = this.directory.resolve("pending.properties");
        this.checkpoint = checkpoint;
        Files.createDirectories(recordsDirectory);
        recover();
    }

    public synchronized void commit(
            TransactionRecord successfulRecord,
            List<WalletSnapshot> before,
            List<Wallet> after) throws IOException {
        ensureOpen();
        Properties journal = journal(successfulRecord, before, snapshots(after), "PREPARED");
        writePropertiesAtomically(journalFile, journal, "LarpSMP pending money transaction");
        checkpoint.accept(JournalCheckpoint.PREPARED, 0);
        boolean committed = false;
        try {
            int writeCount = 0;
            for (Wallet wallet : after) {
                walletRepository.save(wallet);
                checkpoint.accept(JournalCheckpoint.WALLET_WRITTEN, ++writeCount);
            }
            journal.setProperty("state", "COMMITTED");
            writePropertiesAtomically(journalFile, journal, "LarpSMP committed money transaction");
            committed = true;
            checkpoint.accept(JournalCheckpoint.COMMITTED, writeCount);
            writeRecord(successfulRecord);
            checkpoint.accept(JournalCheckpoint.RECORD_WRITTEN, writeCount);
            Files.delete(journalFile);
        } catch (IOException exception) {
            if (committed) {
                try {
                    recover();
                    return;
                } catch (IOException recoveryFailure) {
                    exception.addSuppressed(recoveryFailure);
                }
            } else {
                try {
                    walletRepository.saveAll(before.stream().map(WalletSnapshot::toWallet).toList());
                    Files.deleteIfExists(journalFile);
                } catch (IOException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
            }
            throw exception;
        }
    }

    public synchronized void record(TransactionRecord record) throws IOException {
        ensureOpen();
        writeRecord(record);
    }

    public synchronized List<TransactionRecord> loadAll() throws IOException {
        ensureOpen();
        List<TransactionRecord> records = new ArrayList<>();
        try (var paths = Files.list(recordsDirectory)) {
            for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".properties")).toList()) {
                records.add(readRecord(load(path), "transaction", path));
            }
        }
        records.sort((left, right) -> left.timestamp().compareTo(right.timestamp()));
        return List.copyOf(records);
    }

    private void recover() throws IOException {
        if (!Files.exists(journalFile)) {
            return;
        }
        Properties journal = load(journalFile);
        requireVersion(journal, journalFile);
        String state = required(journal, "state", journalFile);
        TransactionRecord record = readRecord(journal, "transaction", journalFile);
        if (state.equals("COMMITTED")) {
            walletRepository.saveAll(readSnapshots(journal, "after", journalFile)
                    .stream().map(WalletSnapshot::toWallet).toList());
            writeRecord(record);
        } else if (state.equals("PREPARED")) {
            walletRepository.saveAll(readSnapshots(journal, "before", journalFile)
                    .stream().map(WalletSnapshot::toWallet).toList());
            TransactionRecord recoveredFailure = new TransactionRecord(
                    record.transactionId(), record.timestamp(), record.type(),
                    TransactionStatus.STORAGE_FAILURE, record.amount(), record.reason(), record.actorId(),
                    record.sourceWalletId(), record.destinationWalletId(),
                    record.sourceBalanceBefore(), record.sourceBalanceBefore(),
                    record.destinationBalanceBefore(), record.destinationBalanceBefore(),
                    "Recovered and rolled back an incomplete transaction");
            writeRecord(recoveredFailure);
        } else {
            throw new WalletStorageException("Malformed transaction journal: invalid state " + state);
        }
        Files.delete(journalFile);
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    private void writeRecord(TransactionRecord record) throws IOException {
        Properties values = new Properties();
        values.setProperty("schemaVersion", SCHEMA_VERSION);
        putRecord(values, "transaction", record);
        writePropertiesAtomically(
                recordsDirectory.resolve(record.transactionId() + ".properties"),
                values,
                "LarpSMP money transaction");
    }

    private static Properties journal(
            TransactionRecord record,
            List<WalletSnapshot> before,
            List<WalletSnapshot> after,
            String state) {
        Properties values = new Properties();
        values.setProperty("schemaVersion", SCHEMA_VERSION);
        values.setProperty("state", state);
        putRecord(values, "transaction", record);
        putSnapshots(values, "before", before);
        putSnapshots(values, "after", after);
        return values;
    }

    private static List<WalletSnapshot> snapshots(List<Wallet> wallets) {
        return wallets.stream().map(WalletSnapshot::of).toList();
    }

    private static void putSnapshots(Properties values, String prefix, List<WalletSnapshot> snapshots) {
        values.setProperty(prefix + ".count", Integer.toString(snapshots.size()));
        for (int index = 0; index < snapshots.size(); index++) {
            WalletSnapshot snapshot = snapshots.get(index);
            String key = prefix + "." + index + ".";
            values.setProperty(key + "ownerId", snapshot.ownerId().toString());
            values.setProperty(key + "balance", Long.toString(snapshot.balance()));
            values.setProperty(key + "lastKnownUsername", snapshot.lastKnownUsername());
            values.setProperty(key + "startingBalanceGranted",
                    Boolean.toString(snapshot.startingBalanceGranted()));
        }
    }

    private static List<WalletSnapshot> readSnapshots(Properties values, String prefix, Path file)
            throws WalletStorageException {
        int count;
        try {
            count = Integer.parseInt(required(values, prefix + ".count", file));
        } catch (NumberFormatException exception) {
            throw new WalletStorageException("Malformed transaction journal snapshot count", exception);
        }
        if (count < 0 || count > 2) {
            throw new WalletStorageException("Malformed transaction journal snapshot count: " + count);
        }
        List<WalletSnapshot> snapshots = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String key = prefix + "." + index + ".";
            try {
                snapshots.add(new WalletSnapshot(
                        UUID.fromString(required(values, key + "ownerId", file)),
                        Long.parseLong(required(values, key + "balance", file)),
                        required(values, key + "lastKnownUsername", file),
                        parseBoolean(required(values, key + "startingBalanceGranted", file), file)));
            } catch (IllegalArgumentException exception) {
                throw new WalletStorageException("Malformed transaction journal wallet snapshot", exception);
            }
        }
        return snapshots;
    }

    private static void putRecord(Properties values, String prefix, TransactionRecord record) {
        put(values, prefix + ".id", record.transactionId());
        put(values, prefix + ".timestamp", record.timestamp());
        put(values, prefix + ".type", record.type());
        put(values, prefix + ".status", record.status());
        values.setProperty(prefix + ".amount", Long.toString(record.amount()));
        values.setProperty(prefix + ".reason", record.reason());
        put(values, prefix + ".actorId", record.actorId());
        put(values, prefix + ".sourceWalletId", record.sourceWalletId());
        put(values, prefix + ".destinationWalletId", record.destinationWalletId());
        put(values, prefix + ".sourceBalanceBefore", record.sourceBalanceBefore());
        put(values, prefix + ".sourceBalanceAfter", record.sourceBalanceAfter());
        put(values, prefix + ".destinationBalanceBefore", record.destinationBalanceBefore());
        put(values, prefix + ".destinationBalanceAfter", record.destinationBalanceAfter());
        values.setProperty(prefix + ".failureReason", record.failureReason());
    }

    private static TransactionRecord readRecord(Properties values, String prefix, Path file)
            throws WalletStorageException {
        try {
            return new TransactionRecord(
                    UUID.fromString(required(values, prefix + ".id", file)),
                    Instant.parse(required(values, prefix + ".timestamp", file)),
                    TransactionType.valueOf(required(values, prefix + ".type", file)),
                    TransactionStatus.valueOf(required(values, prefix + ".status", file)),
                    Long.parseLong(required(values, prefix + ".amount", file)),
                    values.getProperty(prefix + ".reason", ""),
                    uuid(values, prefix + ".actorId"),
                    uuid(values, prefix + ".sourceWalletId"),
                    uuid(values, prefix + ".destinationWalletId"),
                    number(values, prefix + ".sourceBalanceBefore"),
                    number(values, prefix + ".sourceBalanceAfter"),
                    number(values, prefix + ".destinationBalanceBefore"),
                    number(values, prefix + ".destinationBalanceAfter"),
                    values.getProperty(prefix + ".failureReason", ""));
        } catch (IllegalArgumentException exception) {
            throw new WalletStorageException("Malformed transaction record in " + file, exception);
        }
    }

    private static UUID uuid(Properties values, String key) {
        String value = values.getProperty(key);
        return value == null ? null : UUID.fromString(value);
    }

    private static Long number(Properties values, String key) {
        String value = values.getProperty(key);
        return value == null ? null : Long.parseLong(value);
    }

    private static void put(Properties values, String key, Object value) {
        if (value != null) {
            values.setProperty(key, value.toString());
        }
    }

    private static Properties load(Path file) throws IOException {
        Properties values = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            values.load(input);
        }
        return values;
    }

    private static void writePropertiesAtomically(Path destination, Properties values, String comment)
            throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".tmp");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, comment);
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

    private static void requireVersion(Properties values, Path file) throws WalletStorageException {
        String version = required(values, "schemaVersion", file);
        if (!SCHEMA_VERSION.equals(version)) {
            throw new WalletStorageException("Unsupported transaction schema in " + file + ": " + version);
        }
    }

    private static String required(Properties values, String key, Path file) throws WalletStorageException {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new WalletStorageException("Missing " + key + " in " + file);
        }
        return value;
    }

    private static boolean parseBoolean(String value, Path file) throws WalletStorageException {
        if (!value.equals("true") && !value.equals("false")) {
            throw new WalletStorageException("Invalid boolean in " + file + ": " + value);
        }
        return Boolean.parseBoolean(value);
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Transaction store is closed");
        }
    }
}
