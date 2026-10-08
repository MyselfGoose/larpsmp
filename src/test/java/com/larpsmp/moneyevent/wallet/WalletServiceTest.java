package com.larpsmp.moneyevent.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WalletServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAndLoadsWalletByUuidWithoutAutomaticStartingMoney() throws Exception {
        UUID ownerId = UUID.randomUUID();
        try (WalletService service = service()) {
            Wallet created = service.getOrCreate(ownerId, "akiracreates");
            Wallet loaded = service.find(ownerId).orElseThrow();

            assertEquals(ownerId, created.ownerId());
            assertEquals(0, created.balance());
            assertFalse(created.startingBalanceGranted());
            assertSame(created, loaded);
        }
    }

    @Test
    void explicitlyEligibleCompetitorGetsStartingBalanceExactlyOnce() throws Exception {
        UUID ownerId = UUID.randomUUID();
        try (WalletService service = service()) {
            Wallet first = service.initializeEligibleCompetitor(ownerId, "competitor");
            Wallet second = service.initializeEligibleCompetitor(ownerId, "competitor");

            assertEquals(WalletService.STARTING_BALANCE, first.balance());
            assertTrue(first.startingBalanceGranted());
            assertEquals(200, second.balance());
            assertSame(first, second);
        }
    }

    @Test
    void zeroBalanceSurvivesReloadAndDoesNotReinitialize() throws Exception {
        UUID ownerId = UUID.randomUUID();
        try (WalletService service = service()) {
            service.initializeEligibleCompetitor(ownerId, "competitor");
            service.setBalance(ownerId, 0);
        }

        try (WalletService reopened = service()) {
            Wallet wallet = reopened.initializeEligibleCompetitor(ownerId, "competitor");
            assertEquals(0, wallet.balance());
            assertTrue(wallet.startingBalanceGranted());
        }
    }

    @Test
    void rejectsNegativeBalances() throws Exception {
        UUID ownerId = UUID.randomUUID();
        try (WalletService service = service()) {
            service.getOrCreate(ownerId, "competitor");
            assertThrows(IllegalArgumentException.class, () -> service.setBalance(ownerId, -1));
            assertEquals(0, service.find(ownerId).orElseThrow().balance());
        }
    }

    @Test
    void usernameChangeUpdatesSameWalletAndPersists() throws Exception {
        UUID ownerId = UUID.randomUUID();
        try (WalletService service = service()) {
            Wallet original = service.getOrCreate(ownerId, "old_name");
            Wallet renamed = service.getOrCreate(ownerId, "new_name");
            assertSame(original, renamed);
            assertEquals("new_name", renamed.lastKnownUsername());
        }

        try (WalletService reopened = service()) {
            Wallet loaded = reopened.find(ownerId).orElseThrow();
            assertEquals("new_name", loaded.lastKnownUsername());
        }
        assertEquals(1, walletFileCount());
    }

    @Test
    void persistenceSurvivesClosingAndReopeningStorage() throws Exception {
        UUID ownerId = UUID.randomUUID();
        try (WalletService service = service()) {
            service.initializeEligibleCompetitor(ownerId, "competitor");
            service.setBalance(ownerId, 75);
        }

        try (WalletService reopened = service()) {
            Wallet loaded = reopened.find(ownerId).orElseThrow();
            assertEquals(75, loaded.balance());
            assertEquals("competitor", loaded.lastKnownUsername());
            assertTrue(loaded.startingBalanceGranted());
        }
    }

    @Test
    void repeatedRequestsShareOneAuthoritativeWalletObject() throws Exception {
        UUID ownerId = UUID.randomUUID();
        try (WalletService service = service()) {
            Wallet first = service.getOrCreate(ownerId, "competitor");
            Wallet second = service.getOrCreate(ownerId, "competitor");
            assertSame(first, second);
        }
    }

    @Test
    void malformedStoredDataFailsWithoutReplacement() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Path wallets = temporaryDirectory.resolve("wallets");
        Files.createDirectories(wallets);
        Path walletFile = wallets.resolve(ownerId + ".properties");
        String malformed = "schemaVersion=1\nownerId=" + ownerId
                + "\nbalance=not-a-number\nlastKnownUsername=competitor\nstartingBalanceGranted=false\n";
        Files.writeString(walletFile, malformed);

        try (WalletService service = service()) {
            assertThrows(WalletStorageException.class, () -> service.find(ownerId));
        }
        assertEquals(malformed, Files.readString(walletFile));
    }

    @Test
    void negativeStoredBalanceFailsWithoutStartingBalanceFallback() throws Exception {
        UUID ownerId = UUID.randomUUID();
        Path wallets = temporaryDirectory.resolve("wallets");
        Files.createDirectories(wallets);
        Files.writeString(wallets.resolve(ownerId + ".properties"),
                "schemaVersion=1\nownerId=" + ownerId
                        + "\nbalance=-1\nlastKnownUsername=competitor\nstartingBalanceGranted=false\n");

        try (WalletService service = service()) {
            assertThrows(WalletStorageException.class,
                    () -> service.initializeEligibleCompetitor(ownerId, "competitor"));
        }
    }

    private WalletService service() throws IOException {
        return new WalletService(new FileWalletRepository(temporaryDirectory.resolve("wallets")));
    }

    private long walletFileCount() throws IOException {
        try (var files = Files.list(temporaryDirectory.resolve("wallets"))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".properties")).count();
        }
    }
}
