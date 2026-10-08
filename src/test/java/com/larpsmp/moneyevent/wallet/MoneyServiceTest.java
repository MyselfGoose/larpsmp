package com.larpsmp.moneyevent.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MoneyServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void startingBalanceIsRecordedOnceAndZeroRemainsInitializedAfterRestart() throws Exception {
        UUID walletId = UUID.randomUUID();
        try (ServiceContext context = context()) {
            context.wallets().getOrCreate(walletId, "old_name");
            TransactionResult first = context.money().initializeEligibleCompetitor(walletId, "new_name");
            TransactionResult repeated = context.money().initializeEligibleCompetitor(walletId, "new_name");

            assertEquals(TransactionStatus.SUCCESS, first.status());
            assertEquals(TransactionType.STARTING_BALANCE, first.type());
            assertEquals(0, first.sourceBalanceBefore());
            assertEquals(200, first.sourceBalanceAfter());
            assertEquals(TransactionStatus.ALREADY_INITIALIZED, repeated.status());
            assertEquals(200, context.money().balance(walletId).balance());
            assertEquals("new_name", context.wallets().find(walletId).orElseThrow().lastKnownUsername());
            assertEquals(1, successfulStartingBalanceRecords(context));
            context.money().set(walletId, 0, "test zero persistence", null);
        }

        try (ServiceContext reopened = context()) {
            TransactionResult repeated = reopened.money().initializeEligibleCompetitor(walletId, "new_name");
            assertEquals(TransactionStatus.ALREADY_INITIALIZED, repeated.status());
            assertEquals(0, reopened.money().balance(walletId).balance());
            assertTrue(reopened.wallets().find(walletId).orElseThrow().startingBalanceGranted());
            assertEquals(1, successfulStartingBalanceRecords(reopened));
        }
    }

    @Test
    void startingBalanceStorageFailureRestoresBalanceAndInitializationFlag() throws Exception {
        UUID walletId = UUID.randomUUID();
        FileWalletRepository files = new FileWalletRepository(temporaryDirectory.resolve("wallets"));
        FailingWalletRepository failing = new FailingWalletRepository(files);
        try (ServiceContext context = context(failing)) {
            context.wallets().getOrCreate(walletId, "competitor");
            failing.failNextSave();
            TransactionResult result = context.money().initializeEligibleCompetitor(walletId, "competitor");

            assertEquals(TransactionStatus.STORAGE_FAILURE, result.status());
            Wallet wallet = context.wallets().find(walletId).orElseThrow();
            assertEquals(0, wallet.balance());
            assertFalse(wallet.startingBalanceGranted());
        }
        try (ServiceContext reopened = context()) {
            Wallet wallet = reopened.wallets().find(walletId).orElseThrow();
            assertEquals(0, wallet.balance());
            assertFalse(wallet.startingBalanceGranted());
        }
    }

    @Test
    void startingBalancePreparedCrashRollsBackAndCanBeRetried() throws Exception {
        UUID walletId = UUID.randomUUID();
        try (ServiceContext setup = context()) {
            setup.wallets().getOrCreate(walletId, "competitor");
        }
        try (ServiceContext crashing = context((checkpoint, writeCount) -> {
            if (checkpoint == JournalCheckpoint.WALLET_WRITTEN && writeCount == 1) {
                throw new SimulatedCrash();
            }
        })) {
            assertThrows(SimulatedCrash.class,
                    () -> crashing.money().initializeEligibleCompetitor(walletId, "competitor"));
        }

        try (ServiceContext recovered = context()) {
            Wallet wallet = recovered.wallets().find(walletId).orElseThrow();
            assertEquals(0, wallet.balance());
            assertFalse(wallet.startingBalanceGranted());
            assertEquals(TransactionStatus.SUCCESS,
                    recovered.money().initializeEligibleCompetitor(walletId, "competitor").status());
            assertEquals(200, recovered.money().balance(walletId).balance());
        }
    }

    @ParameterizedTest
    @MethodSource("crashPoints")
    void journalRecoveryIsNeverMixedAndNeverDuplicatesRecords(
            JournalCheckpoint crashAt, int crashWriteCount, boolean committedExpected) throws Exception {
        Path caseDirectory = temporaryDirectory.resolve(crashAt + "-" + crashWriteCount);
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        try (ServiceContext setup = context(caseDirectory)) {
            setup.wallets().getOrCreate(source, "source");
            setup.wallets().getOrCreate(destination, "destination");
            setup.money().set(source, 100, "fund crash audit", null);
        }

        try (ServiceContext crashing = context(caseDirectory, (checkpoint, writeCount) -> {
            if (checkpoint == crashAt && writeCount == crashWriteCount) {
                throw new SimulatedCrash();
            }
        })) {
            assertThrows(SimulatedCrash.class,
                    () -> crashing.money().transfer(source, destination, 40, "crash audit", null));
        }

        try (ServiceContext recovered = context(caseDirectory)) {
            assertEquals(committedExpected ? 60 : 100, recovered.money().balance(source).balance());
            assertEquals(committedExpected ? 40 : 0, recovered.money().balance(destination).balance());
            List<TransactionRecord> recoveredRecords = recovered.transactions().loadAll().stream()
                    .filter(record -> record.reason().equals("crash audit"))
                    .toList();
            assertEquals(1, recoveredRecords.size());
            assertEquals(committedExpected ? TransactionStatus.SUCCESS : TransactionStatus.STORAGE_FAILURE,
                    recoveredRecords.getFirst().status());
        }
    }

    private static List<Arguments> crashPoints() {
        return List.of(
                Arguments.of(JournalCheckpoint.PREPARED, 0, false),
                Arguments.of(JournalCheckpoint.WALLET_WRITTEN, 1, false),
                Arguments.of(JournalCheckpoint.WALLET_WRITTEN, 2, false),
                Arguments.of(JournalCheckpoint.COMMITTED, 2, true),
                Arguments.of(JournalCheckpoint.RECORD_WRITTEN, 2, true));
    }

    @Test
    void readsAddsRemovesAndSetsZero() throws Exception {
        UUID walletId = UUID.randomUUID();
        try (ServiceContext context = context()) {
            context.wallets().getOrCreate(walletId, "competitor");
            assertEquals(0, context.money().balance(walletId).balance());

            TransactionResult added = context.money().add(walletId, 50, "test add", null);
            assertTrue(added.successful());
            assertEquals(0, added.sourceBalanceBefore());
            assertEquals(50, added.sourceBalanceAfter());

            assertTrue(context.money().remove(walletId, 20, "test remove", null).successful());
            assertEquals(30, context.money().balance(walletId).balance());
            assertTrue(context.money().set(walletId, 0, "test zero", null).successful());
            assertEquals(0, context.money().balance(walletId).balance());
        }
    }

    @Test
    void transfersAndPersistsBothWalletsAcrossRestart() throws Exception {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        try (ServiceContext context = context()) {
            context.wallets().getOrCreate(source, "source");
            context.wallets().getOrCreate(destination, "destination");
            context.money().set(source, 200, "fund source", null);

            TransactionResult result = context.money().transfer(source, destination, 75, "test transfer", source);
            assertTrue(result.successful());
            assertEquals(200, result.sourceBalanceBefore());
            assertEquals(125, result.sourceBalanceAfter());
            assertEquals(0, result.destinationBalanceBefore());
            assertEquals(75, result.destinationBalanceAfter());
            assertNotNull(result.transactionId());
        }

        try (ServiceContext reopened = context()) {
            assertEquals(125, reopened.money().balance(source).balance());
            assertEquals(75, reopened.money().balance(destination).balance());
        }
    }

    @Test
    void rejectsZeroNegativeInsufficientSelfTransferAndOverflowWithoutChanges() throws Exception {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        try (ServiceContext context = context()) {
            context.wallets().getOrCreate(source, "source");
            context.wallets().getOrCreate(destination, "destination");
            context.money().set(source, 10, "fund", null);
            context.money().set(destination, Long.MAX_VALUE, "max", null);

            assertEquals(TransactionStatus.INVALID_AMOUNT,
                    context.money().add(source, 0, "zero", null).status());
            assertEquals(TransactionStatus.INVALID_AMOUNT,
                    context.money().remove(source, -1, "negative", null).status());
            assertEquals(TransactionStatus.INSUFFICIENT_FUNDS,
                    context.money().remove(source, 11, "too much", null).status());
            assertEquals(TransactionStatus.INSUFFICIENT_FUNDS,
                    context.money().transfer(source, destination, 11, "too much", null).status());
            assertEquals(TransactionStatus.SAME_WALLET,
                    context.money().transfer(source, source, 1, "self", null).status());
            assertEquals(TransactionStatus.INVALID_AMOUNT,
                    context.money().add(destination, 1, "overflow", null).status());
            assertEquals(TransactionStatus.INVALID_AMOUNT,
                    context.money().transfer(source, destination, 1, "overflow", null).status());

            assertEquals(10, context.money().balance(source).balance());
            assertEquals(Long.MAX_VALUE, context.money().balance(destination).balance());
        }
    }

    @Test
    void missingWalletAndBlankReasonProduceFailureRecords() throws Exception {
        UUID missing = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        try (ServiceContext context = context()) {
            context.wallets().getOrCreate(existing, "existing");
            TransactionResult missingResult = context.money().add(missing, 1, "missing", null);
            TransactionResult blankReason = context.money().add(existing, 1, "  ", null);

            assertEquals(TransactionStatus.WALLET_NOT_FOUND, missingResult.status());
            assertEquals(TransactionStatus.INVALID_REASON, blankReason.status());
            assertEquals(0, context.money().balance(existing).balance());
            List<TransactionRecord> records = context.transactions().loadAll();
            assertTrue(records.stream().anyMatch(record ->
                    record.transactionId().equals(missingResult.transactionId())
                            && record.status() == TransactionStatus.WALLET_NOT_FOUND));
            assertTrue(records.stream().anyMatch(record ->
                    record.transactionId().equals(blankReason.transactionId())
                            && !record.failureReason().isBlank()));
        }
    }

    @Test
    void successfulRecordContainsExactMetadataAndBalances() throws Exception {
        UUID walletId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        try (ServiceContext context = context()) {
            context.wallets().getOrCreate(walletId, "competitor");
            TransactionResult result = context.money().add(walletId, 25, "capture placeholder", actorId);
            TransactionRecord record = context.transactions().loadAll().stream()
                    .filter(candidate -> candidate.transactionId().equals(result.transactionId()))
                    .findFirst().orElseThrow();

            assertEquals(TransactionType.ADD, record.type());
            assertEquals(TransactionStatus.SUCCESS, record.status());
            assertEquals(25, record.amount());
            assertEquals("capture placeholder", record.reason());
            assertEquals(actorId, record.actorId());
            assertEquals(walletId, record.sourceWalletId());
            assertEquals(0, record.sourceBalanceBefore());
            assertEquals(25, record.sourceBalanceAfter());
            assertNotNull(record.timestamp());
        }
    }

    @Test
    void successfulSingleWalletChangesPersistAfterRestart() throws Exception {
        UUID walletId = UUID.randomUUID();
        try (ServiceContext context = context()) {
            context.wallets().getOrCreate(walletId, "competitor");
            context.money().add(walletId, 100, "add", null);
            context.money().remove(walletId, 40, "remove", null);
        }
        try (ServiceContext reopened = context()) {
            assertEquals(60, reopened.money().balance(walletId).balance());
        }
    }

    @Test
    void simulatedSecondWalletWriteFailureRollsBackTransfer() throws Exception {
        UUID source = UUID.randomUUID();
        UUID destination = UUID.randomUUID();
        FileWalletRepository files = new FileWalletRepository(temporaryDirectory.resolve("wallets"));
        FailingWalletRepository failing = new FailingWalletRepository(files);
        try (ServiceContext context = context(failing)) {
            context.wallets().getOrCreate(source, "source");
            context.wallets().getOrCreate(destination, "destination");
            context.money().set(source, 100, "fund", null);
            failing.failOnSecondFollowingSave();

            TransactionResult result = context.money().transfer(source, destination, 50, "failure test", null);
            assertEquals(TransactionStatus.STORAGE_FAILURE, result.status());
            assertEquals(100, context.money().balance(source).balance());
            assertEquals(0, context.money().balance(destination).balance());
        }

        try (ServiceContext reopened = context()) {
            assertEquals(100, reopened.money().balance(source).balance());
            assertEquals(0, reopened.money().balance(destination).balance());
        }
    }

    @Test
    void concurrentRemovalsCannotOverspend() throws Exception {
        UUID walletId = UUID.randomUUID();
        try (ServiceContext context = context(); var executor = Executors.newFixedThreadPool(8)) {
            context.wallets().getOrCreate(walletId, "competitor");
            context.money().set(walletId, 200, "fund", null);
            List<Future<TransactionResult>> futures = new ArrayList<>();
            for (int index = 0; index < 400; index++) {
                futures.add(executor.submit(() -> context.money().remove(walletId, 1, "concurrent", null)));
            }
            long successes = 0;
            for (Future<TransactionResult> future : futures) {
                if (future.get().successful()) {
                    successes++;
                }
            }
            assertEquals(200, successes);
            assertEquals(0, context.money().balance(walletId).balance());
        }
    }

    private ServiceContext context() throws IOException {
        return context(temporaryDirectory);
    }

    private ServiceContext context(Path directory) throws IOException {
        return context(new FileWalletRepository(directory.resolve("wallets")), directory, (checkpoint, count) -> { });
    }

    private ServiceContext context(java.util.function.BiConsumer<JournalCheckpoint, Integer> checkpoint)
            throws IOException {
        return context(new FileWalletRepository(temporaryDirectory.resolve("wallets")), temporaryDirectory, checkpoint);
    }

    private ServiceContext context(
            Path directory, java.util.function.BiConsumer<JournalCheckpoint, Integer> checkpoint)
            throws IOException {
        return context(new FileWalletRepository(directory.resolve("wallets")), directory, checkpoint);
    }

    private ServiceContext context(WalletRepository repository) throws IOException {
        return context(repository, temporaryDirectory, (checkpoint, count) -> { });
    }

    private ServiceContext context(
            WalletRepository repository,
            Path directory,
            java.util.function.BiConsumer<JournalCheckpoint, Integer> checkpoint) throws IOException {
        FileTransactionStore transactions =
                new FileTransactionStore(repository, directory.resolve("transactions"), checkpoint);
        WalletService wallets = new WalletService(repository);
        MoneyService money = new MoneyService(wallets, transactions, message -> { });
        return new ServiceContext(wallets, transactions, money);
    }

    private static long successfulStartingBalanceRecords(ServiceContext context) throws IOException {
        return context.transactions().loadAll().stream()
                .filter(record -> record.type() == TransactionType.STARTING_BALANCE)
                .filter(TransactionRecord::successful)
                .count();
    }

    private record ServiceContext(
            WalletService wallets, FileTransactionStore transactions, MoneyService money)
            implements AutoCloseable {
        @Override
        public void close() throws IOException {
            money.close();
        }
    }

    private static final class FailingWalletRepository implements WalletRepository {
        private final WalletRepository delegate;
        private int savesUntilFailure = -1;

        private FailingWalletRepository(WalletRepository delegate) {
            this.delegate = delegate;
        }

        void failOnSecondFollowingSave() {
            savesUntilFailure = 2;
        }

        void failNextSave() {
            savesUntilFailure = 1;
        }

        @Override
        public Optional<Wallet> load(UUID ownerId) throws IOException {
            return delegate.load(ownerId);
        }

        @Override
        public void save(Wallet wallet) throws IOException {
            if (savesUntilFailure > 0 && --savesUntilFailure == 0) {
                throw new IOException("simulated wallet write failure");
            }
            delegate.save(wallet);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }

    private static final class SimulatedCrash extends RuntimeException {
    }
}
