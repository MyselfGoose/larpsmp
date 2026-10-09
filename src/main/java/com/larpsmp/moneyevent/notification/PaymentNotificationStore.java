package com.larpsmp.moneyevent.notification;

import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.TransactionRecord;
import com.larpsmp.moneyevent.wallet.TransactionStatus;
import com.larpsmp.moneyevent.wallet.TransactionType;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

public final class PaymentNotificationStore {
    private final Path pendingDirectory;
    private final Path deliveredDirectory;
    private final MoneyService moneyService;
    private final Consumer<String> errorLogger;

    public PaymentNotificationStore(Path directory, MoneyService moneyService, Consumer<String> errorLogger)
            throws IOException {
        pendingDirectory = directory.resolve("pending").toAbsolutePath().normalize();
        deliveredDirectory = directory.resolve("delivered").toAbsolutePath().normalize();
        this.moneyService = moneyService;
        this.errorLogger = errorLogger;
        Files.createDirectories(pendingDirectory);
        Files.createDirectories(deliveredDirectory);
    }

    public synchronized void enqueue(UUID recipientId, UUID transactionId) throws IOException {
        Path recipientDirectory = pendingDirectory.resolve(recipientId.toString());
        Files.createDirectories(recipientDirectory);
        Properties values = new Properties();
        values.setProperty("transactionId", transactionId.toString());
        writeAtomically(recipientDirectory.resolve(transactionId + ".properties"), values);
    }

    /**
     * Claims each notification only after the player has joined, then delivers it at most once.
     * A process failure after the atomic claim but before sending can lose that message, but cannot
     * repeat it or affect the already-committed transfer.
     */
    public synchronized void deliver(UUID recipientId, Consumer<String> messageConsumer) {
        Path recipientDirectory = pendingDirectory.resolve(recipientId.toString());
        if (!Files.isDirectory(recipientDirectory)) {
            return;
        }
        try (var paths = Files.list(recipientDirectory)) {
            List<Path> pending = paths.filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .sorted().toList();
            for (Path file : pending) {
                UUID transactionId = UUID.fromString(
                        file.getFileName().toString().replace(".properties", ""));
                Path archiveDirectory = deliveredDirectory.resolve(recipientId.toString());
                Files.createDirectories(archiveDirectory);
                moveAtomically(file, archiveDirectory.resolve(file.getFileName()));
                deliverTransaction(recipientId, transactionId, messageConsumer);
            }
            try (var remaining = Files.list(recipientDirectory)) {
                if (remaining.findAny().isEmpty()) {
                    Files.deleteIfExists(recipientDirectory);
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            errorLogger.accept("Could not deliver payment notifications for " + recipientId + ": "
                    + exception.getMessage());
        }
    }

    private void deliverTransaction(UUID recipientId, UUID transactionId, Consumer<String> messageConsumer)
            throws IOException {
        TransactionRecord transaction = moneyService.transaction(transactionId).orElse(null);
        var recipientWallet = moneyService.account(recipientId).orElse(null);
        if (transaction == null
                || transaction.type() != TransactionType.TRANSFER
                || transaction.status() != TransactionStatus.SUCCESS
                || recipientWallet == null
                || transaction.destinationWalletId() == null
                || !recipientWallet.accountId().equals(transaction.destinationWalletId())) {
            errorLogger.accept("Ignoring invalid payment notification transaction " + transactionId);
            return;
        }
        String senderName = transaction.sourceWalletId() == null
                ? "Someone"
                : moneyService.accountByAccountId(transaction.sourceWalletId())
                        .map(account -> account.lastKnownUsername())
                        .orElse(transaction.sourceWalletId().toString());
        messageConsumer.accept("While you were offline, " + senderName + " sent you $"
                + transaction.amount() + ". Your balance is now $" + transaction.destinationBalanceAfter() + ".");
    }

    private static void writeAtomically(Path destination, Properties values) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), "notification-", ".tmp");
        boolean moved = false;
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                values.store(output, "LarpSMP offline payment notification");
            }
            moveAtomically(temporary, destination);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static void moveAtomically(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
