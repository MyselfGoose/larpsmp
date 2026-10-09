package com.larpsmp.moneyevent.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class MoneyServiceTest {

    @Test
    void signupCreatesWalletWithStartingBalanceOnce() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = open()) {
            TestDatabaseSupport.RegisteredPlayer player = fixture.register("StartPlayer");
            WalletAccount wallet = fixture.money().account(player.minecraftUuid()).orElseThrow();
            assertEquals(MoneyService.STARTING_BALANCE, wallet.balance());
            assertEquals(player.minecraftUuid(), wallet.playerId());

            long startingRecords = fixture.money().allTransactions().stream()
                    .filter(record -> record.type() == TransactionType.STARTING_BALANCE
                            && record.status() == TransactionStatus.SUCCESS
                            && wallet.accountId().equals(record.sourceWalletId()))
                    .count();
            assertEquals(1, startingRecords);
        }
    }

    @Test
    void transferAddRemoveSetPersistAcrossServiceReload() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = open()) {
            TestDatabaseSupport.RegisteredPlayer source = fixture.register("SourcePay");
            TestDatabaseSupport.RegisteredPlayer destination = fixture.register("DestPay");
            UUID sourceId = source.minecraftUuid();
            UUID destinationId = destination.minecraftUuid();

            TransactionResult transfer = fixture.money().transfer(
                    sourceId, destinationId, 50, "test transfer", sourceId);
            assertEquals(TransactionStatus.SUCCESS, transfer.status());
            assertEquals(150, fixture.money().balance(sourceId).balance());
            assertEquals(250, fixture.money().balance(destinationId).balance());

            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().add(destinationId, 10, "bonus", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().remove(destinationId, 20, "fee", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().set(sourceId, 100, "reset", null).status());

            try (MoneyService reopened = new MoneyService(
                    fixture.accounts(), fixture.moneyRepository(), message -> { })) {
                assertEquals(100, reopened.balance(sourceId).balance());
                assertEquals(240, reopened.balance(destinationId).balance());
            }
        }
    }

    @Test
    void transferRejectsInsufficientFundsAndSameWallet() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = open()) {
            TestDatabaseSupport.RegisteredPlayer source = fixture.register("PoorSource");
            TestDatabaseSupport.RegisteredPlayer destination = fixture.register("RichDest");

            TransactionResult insufficient = fixture.money().transfer(
                    source.minecraftUuid(), destination.minecraftUuid(), 201, "too much", source.minecraftUuid());
            assertEquals(TransactionStatus.INSUFFICIENT_FUNDS, insufficient.status());
            assertEquals(200, fixture.money().balance(source.minecraftUuid()).balance());

            TransactionResult same = fixture.money().transfer(
                    source.minecraftUuid(), source.minecraftUuid(), 1, "self", source.minecraftUuid());
            assertEquals(TransactionStatus.SAME_WALLET, same.status());
        }
    }

    @Test
    void concurrentTransfersDoNotOverdraft() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = open()) {
            TestDatabaseSupport.RegisteredPlayer source = fixture.register("ConcurrentSource");
            TestDatabaseSupport.RegisteredPlayer destination = fixture.register("ConcurrentDest");
            UUID sourceId = source.minecraftUuid();
            UUID destinationId = destination.minecraftUuid();

            var executor = Executors.newFixedThreadPool(8);
            List<Future<TransactionResult>> futures = new ArrayList<>();
            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(() -> fixture.money().transfer(
                        sourceId, destinationId, 20, "concurrent", sourceId)));
            }
            int successes = 0;
            for (Future<TransactionResult> future : futures) {
                if (future.get().successful()) {
                    successes++;
                }
            }
            executor.shutdownNow();

            assertEquals(10, successes);
            assertEquals(0, fixture.money().balance(sourceId).balance());
            assertEquals(400, fixture.money().balance(destinationId).balance());
        }
    }

    @Test
    void adminAuditMetadataIsPersisted() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = open()) {
            TestDatabaseSupport.RegisteredPlayer target = fixture.register("AuditTarget");
            MoneyAuditContext context = new MoneyAuditContext(
                    "MONEY_GIVE", "/larp money give", "CONSOLE", "CONSOLE");
            TransactionResult result = fixture.money().add(
                    target.minecraftUuid(), 5, "audit reason", null, context);
            assertTrue(result.successful());

            TransactionRecord record = fixture.money().transaction(result.transactionId()).orElseThrow();
            assertEquals("MONEY_GIVE", record.authorizationAction());
            assertEquals("/larp money give", record.command());
            assertEquals("CONSOLE", record.actorUsername());
            assertEquals("CONSOLE", record.senderType());
            assertEquals("audit reason", record.reason());
        }
    }

    private static TestDatabaseSupport.Fixture open() throws Exception {
        TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("MoneyServiceTest");
        assumeTrue(fixture != null, "No reachable Postgres for money tests");
        return fixture;
    }
}
