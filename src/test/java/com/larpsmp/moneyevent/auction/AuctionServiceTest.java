package com.larpsmp.moneyevent.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import com.larpsmp.moneyevent.wallet.TransactionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuctionServiceTest {

    @Test
    void biddingRejectsOverBalanceNonRepAndCountdown() throws Exception {
        try (AuctionFixture fixture = open()) {
            Team blue = fixture.createTeam("AucBlue", TeamColor.BLUE);
            Team red = fixture.createTeam("AucRed", TeamColor.RED);
            Team yellow = fixture.createTeam("AucYellow", TeamColor.YELLOW);
            var blueRep = fixture.addMember(blue, "AucBRep");
            var redRep = fixture.addMember(red, "AucRRep");
            var yellowRep = fixture.addMember(yellow, "AucYRep");
            var outsider = fixture.addMember(blue, "AucOut");

            assertInstanceOf(AuctionService.SessionStartResult.Started.class, fixture.service().startSession());
            assertInstanceOf(AuctionService.RepResult.Ok.class,
                    fixture.service().registerRepresentative(fixture.accountId(blueRep)));
            assertInstanceOf(AuctionService.RepResult.Ok.class,
                    fixture.service().registerRepresentative(fixture.accountId(redRep)));
            assertInstanceOf(AuctionService.RepResult.Ok.class,
                    fixture.service().registerRepresentative(fixture.accountId(yellowRep)));
            assertInstanceOf(AuctionService.OpenLotResult.Opened.class,
                    fixture.service().openLot("Something Explosive", 20));

            assertInstanceOf(AuctionService.BidResult.NotRepresentative.class,
                    fixture.service().bid(fixture.accountId(outsider), 25));
            assertInstanceOf(AuctionService.BidResult.TooLow.class,
                    fixture.service().bid(fixture.accountId(blueRep), 19));
            assertInstanceOf(AuctionService.BidResult.Accepted.class,
                    fixture.service().bid(fixture.accountId(blueRep), 20));
            assertInstanceOf(AuctionService.BidResult.InsufficientFunds.class,
                    fixture.service().bid(fixture.accountId(redRep), 10_000));

            assertInstanceOf(AuctionService.AdminResult.Ok.class, fixture.service().countdown());
            assertInstanceOf(AuctionService.BidResult.CountdownLocked.class,
                    fixture.service().bid(fixture.accountId(redRep), 30));
        }
    }

    @Test
    void paymentToAmiraMarksLotPaidIdempotentlyAndForfeitFallsBack() throws Exception {
        try (AuctionFixture fixture = open()) {
            Team blue = fixture.createTeam("PayBlue", TeamColor.BLUE);
            Team red = fixture.createTeam("PayRed", TeamColor.RED);
            Team yellow = fixture.createTeam("PayYellow", TeamColor.YELLOW);
            var blueRep = fixture.addMember(blue, "PayB");
            var redRep = fixture.addMember(red, "PayR");
            var yellowRep = fixture.addMember(yellow, "PayY");
            var amira = fixture.registerAmira();
            assertInstanceOf(AuctionService.PayeeResult.Changed.class,
                    fixture.service().setPaymentRecipient(amira.username(), "admin", "console"));

            fixture.service().startSession();
            fixture.service().registerRepresentative(fixture.accountId(blueRep));
            fixture.service().registerRepresentative(fixture.accountId(redRep));
            fixture.service().registerRepresentative(fixture.accountId(yellowRep));
            fixture.service().openLot("Mystery", 10);

            assertInstanceOf(AuctionService.BidResult.Accepted.class,
                    fixture.service().bid(fixture.accountId(blueRep), 15));
            assertInstanceOf(AuctionService.BidResult.Accepted.class,
                    fixture.service().bid(fixture.accountId(redRep), 25));
            assertInstanceOf(AuctionService.CloseLotResult.AwaitingPayment.class, fixture.service().closeLot());

            // Wrong amount ignored.
            TransactionResult wrong = fixture.money().transfer(
                    redRep.minecraftUuid(), amira.minecraftUuid(), 24, "wrong", redRep.minecraftUuid());
            assertTrue(wrong.successful());
            assertEquals(AuctionLotStatus.AWAITING_PAYMENT, fixture.service().activeLot().orElseThrow().status());

            TransactionResult paid = fixture.money().transfer(
                    redRep.minecraftUuid(), amira.minecraftUuid(), 25, "auction win", redRep.minecraftUuid());
            assertTrue(paid.successful());
            AuctionLot paidLot = fixture.service().activeLot().orElseThrow();
            assertEquals(AuctionLotStatus.PAID, paidLot.status());
            assertTrue(fixture.money().hasAuctionPaymentSettlement(paidLot.lotId().toString()));

            // Idempotent settlement.
            assertTrue(fixture.money().ensureAuctionPaymentSettlement(
                    paidLot.lotId().toString(), paid.transactionId(), 25));

            assertInstanceOf(AuctionService.AdminResult.Ok.class, fixture.service().markDelivered());
            assertTrue(fixture.service().activeLot().isEmpty());
        }
    }

    @Test
    void forfeitOffersPreviousBidderWhoCanStillAfford() throws Exception {
        try (AuctionFixture fixture = open()) {
            Team blue = fixture.createTeam("FbBlue", TeamColor.BLUE);
            Team red = fixture.createTeam("FbRed", TeamColor.RED);
            Team yellow = fixture.createTeam("FbYellow", TeamColor.YELLOW);
            var blueRep = fixture.addMember(blue, "FbB");
            var redRep = fixture.addMember(red, "FbR");
            var yellowRep = fixture.addMember(yellow, "FbY");
            var amira = fixture.registerAmira();
            fixture.service().setPaymentRecipient(amira.username(), "admin", "console");

            fixture.service().startSession();
            fixture.service().registerRepresentative(fixture.accountId(blueRep));
            fixture.service().registerRepresentative(fixture.accountId(redRep));
            fixture.service().registerRepresentative(fixture.accountId(yellowRep));
            fixture.service().openLot("Fallback Lot", 10);

            fixture.service().bid(fixture.accountId(blueRep), 20);
            fixture.service().bid(fixture.accountId(redRep), 40);
            fixture.service().closeLot();

            // Drain winner so they cannot pay, then forfeit.
            long redBalance = fixture.money().account(redRep.minecraftUuid()).orElseThrow().balance();
            fixture.money().remove(redRep.minecraftUuid(), redBalance, "drain", null);

            AuctionService.ForfeitResult forfeit = fixture.service().forfeit();
            assertInstanceOf(AuctionService.ForfeitResult.Offered.class, forfeit);
            AuctionLot offered = ((AuctionService.ForfeitResult.Offered) forfeit).lot();
            assertEquals(fixture.accountId(blueRep), offered.owedAccountId());
            assertEquals(20L, offered.owedAmount());
        }
    }

    @Test
    void payeeCommandPersistsRejectsUnknownAndDrivesPaymentMatch() throws Exception {
        try (AuctionFixture fixture = open()) {
            Team blue = fixture.createTeam("PayeeBlue", TeamColor.BLUE);
            Team red = fixture.createTeam("PayeeRed", TeamColor.RED);
            Team yellow = fixture.createTeam("PayeeYellow", TeamColor.YELLOW);
            var blueRep = fixture.addMember(blue, "PayeeB");
            var redRep = fixture.addMember(red, "PayeeR");
            var yellowRep = fixture.addMember(yellow, "PayeeY");
            var hostA = fixture.registerAmira();
            var hostB = fixture.registerExtraPayee(
                    "hostb_" + Integer.toHexString(UUID.randomUUID().hashCode()));

            assertInstanceOf(AuctionService.PayeeResult.AccountNotFound.class,
                    fixture.service().setPaymentRecipient("nobody_here", "admin", "console"));

            AuctionService.PayeeResult setA = fixture.service().setPaymentRecipient(
                    hostA.username(), "admin", "console");
            assertInstanceOf(AuctionService.PayeeResult.Changed.class, setA);
            assertEquals(hostA.username(), fixture.service().config().paymentUsername());

            AuctionService.PayeeResult setB = fixture.service().setPaymentRecipient(
                    hostB.username(), "admin", "player");
            assertInstanceOf(AuctionService.PayeeResult.Changed.class, setB);
            assertEquals(hostB.username(),
                    ((AuctionService.PayeeResult.Changed) setB).newUsername());

            // Survives restore from Postgres.
            AuctionService reopened = fixture.reopenService();
            reopened.restoreFromDatabase();
            assertEquals(hostB.username(), reopened.config().paymentUsername());

            // Wire payment matching on the restored service and run a lot.
            fixture.money().addTransferSuccessListener(reopened::onTransferSuccess);
            reopened.startSession();
            reopened.registerRepresentative(fixture.accountId(blueRep));
            reopened.registerRepresentative(fixture.accountId(redRep));
            reopened.registerRepresentative(fixture.accountId(yellowRep));
            reopened.openLot("Payee Lot", 10);
            reopened.bid(fixture.accountId(blueRep), 30);
            reopened.closeLot();

            TransactionResult toOldHost = fixture.money().transfer(
                    blueRep.minecraftUuid(), hostA.minecraftUuid(), 30, "wrong host",
                    blueRep.minecraftUuid());
            assertTrue(toOldHost.successful());
            assertEquals(AuctionLotStatus.AWAITING_PAYMENT, reopened.activeLot().orElseThrow().status());

            TransactionResult toNewHost = fixture.money().transfer(
                    blueRep.minecraftUuid(), hostB.minecraftUuid(), 30, "correct host",
                    blueRep.minecraftUuid());
            assertTrue(toNewHost.successful());
            assertEquals(AuctionLotStatus.PAID, reopened.activeLot().orElseThrow().status());
        }
    }

    @Test
    void representativesLockAfterFirstLotAndSessionRestores() throws Exception {
        try (AuctionFixture fixture = open()) {
            Team blue = fixture.createTeam("LockBlue", TeamColor.BLUE);
            Team red = fixture.createTeam("LockRed", TeamColor.RED);
            Team yellow = fixture.createTeam("LockYellow", TeamColor.YELLOW);
            var blueRep = fixture.addMember(blue, "LockB");
            var redRep = fixture.addMember(red, "LockR");
            var yellowRep = fixture.addMember(yellow, "LockY");
            var blueAlt = fixture.addMember(blue, "LockB2");

            fixture.service().startSession();
            fixture.service().registerRepresentative(fixture.accountId(blueRep));
            fixture.service().registerRepresentative(fixture.accountId(redRep));
            fixture.service().registerRepresentative(fixture.accountId(yellowRep));
            fixture.service().openLot("Lock Test", 5);

            assertInstanceOf(AuctionService.RepResult.Locked.class,
                    fixture.service().registerRepresentative(fixture.accountId(blueAlt)));

            UUID sessionId = fixture.service().activeSession().orElseThrow().sessionId();
            UUID lotId = fixture.service().activeLot().orElseThrow().lotId();

            AuctionService reopened = fixture.reopenService();
            reopened.restoreFromDatabase();
            assertEquals(sessionId, reopened.activeSession().orElseThrow().sessionId());
            assertEquals(lotId, reopened.activeLot().orElseThrow().lotId());
            assertEquals(AuctionLotStatus.OPEN, reopened.activeLot().orElseThrow().status());
        }
    }

    private static AuctionFixture open() throws Exception {
        TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("AuctionServiceTest");
        assumeTrue(fixture != null, "No reachable Postgres for auction service tests");
        return new AuctionFixture(fixture);
    }

    private static final class AuctionFixture implements AutoCloseable {
        private final TestDatabaseSupport.Fixture fixture;
        private final TeamService teams;
        private final List<UUID> createdTeams = new ArrayList<>();
        private final JdbcAuctionRepository repository;
        private final List<String> broadcasts = new ArrayList<>();
        private final List<String> extraPayees = new ArrayList<>();
        private AuctionService service;
        private final String amiraUsername = AuctionConfig.DEFAULT_PAYMENT_USERNAME
                + "_"
                + Integer.toHexString(UUID.randomUUID().hashCode());

        AuctionFixture(TestDatabaseSupport.Fixture fixture) throws Exception {
            this.fixture = fixture;
            this.teams = new TeamService(
                    new JdbcTeamRepository(fixture.dataSource()),
                    fixture.accounts(),
                    message -> {
                    });
            fixture.money().setTeamService(teams);
            teams.setCompetitorEnrollmentListener(accountId -> {
                try {
                    fixture.money().grantCompetitorStartingBalanceIfNeeded(accountId);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            this.repository = new JdbcAuctionRepository(fixture.dataSource());
            this.service = newService(AuctionConfig.DEFAULT_PAYMENT_USERNAME, true);
            scrubAuctionTables();
            scrubAllMemberships();
        }

        AuctionService service() {
            return service;
        }

        MoneyService money() {
            return fixture.money();
        }

        AuctionService reopenService() {
            this.service = newService(AuctionConfig.DEFAULT_PAYMENT_USERNAME, false);
            return service;
        }

        private AuctionService newService(String paymentUsername, boolean wireTransferListener) {
            AuctionService created = new AuctionService(
                    new AuctionConfig(paymentUsername),
                    repository,
                    fixture.money(),
                    teams,
                    fixture.accounts(),
                    message -> {
                    });
            created.addListener(broadcasts::add);
            if (wireTransferListener) {
                fixture.money().addTransferSuccessListener(created::onTransferSuccess);
            }
            return created;
        }

        Team createTeam(String name, TeamColor color) {
            var result = teams.createTeam(name + "-" + UUID.randomUUID().toString().substring(0, 6), color);
            assertTrue(result.success(), result.error());
            createdTeams.add(result.team().id());
            return result.team();
        }

        TestDatabaseSupport.RegisteredPlayer addMember(Team team, String prefix) throws Exception {
            TestDatabaseSupport.RegisteredPlayer player = fixture.register(prefix);
            Account account = fixture.accounts().findByUsername(player.username()).orElseThrow();
            assertTrue(teams.assignMember(account.id(), team.id()).success());
            assertTrue(teams.setTier(account.id(), PlayerTier.TIER_3).success());
            return player;
        }

        TestDatabaseSupport.RegisteredPlayer registerAmira() throws Exception {
            return registerExtraPayee(amiraUsername);
        }

        TestDatabaseSupport.RegisteredPlayer registerExtraPayee(String username) throws Exception {
            UUID minecraftUuid = UUID.randomUUID();
            String unique = username.toLowerCase(java.util.Locale.ROOT);
            if (!unique.equals(amiraUsername) && !extraPayees.contains(unique)) {
                extraPayees.add(unique);
            }
            fixture.accounts().createAccountWithIdentity(
                    unique,
                    unique + "@example.com",
                    "test-hash",
                    minecraftUuid,
                    unique);
            return new TestDatabaseSupport.RegisteredPlayer(minecraftUuid, unique);
        }

        UUID accountId(TestDatabaseSupport.RegisteredPlayer player) throws Exception {
            return fixture.accountId(player);
        }

        private void scrubAuctionTables() throws Exception {
            try (var connection = fixture.dataSource().getConnection();
                 var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM auction_bids");
                statement.executeUpdate(
                        "UPDATE auction_runtime SET active_session_id = NULL, active_lot_id = NULL, "
                                + "payment_username = 'akiracreates'");
                statement.executeUpdate("DELETE FROM auction_lots");
                statement.executeUpdate("DELETE FROM auction_representatives");
                statement.executeUpdate("DELETE FROM auction_sessions");
            }
        }

        private void scrubAllMemberships() throws Exception {
            // Shared Postgres may retain roster rows from other suites; openLot requires a
            // representative for every team that currently has members.
            try (var connection = fixture.dataSource().getConnection();
                 var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM team_memberships");
                statement.executeUpdate("DELETE FROM competitor_tiers");
            }
        }

        @Override
        public void close() throws Exception {
            scrubAuctionTables();
            for (UUID teamId : createdTeams) {
                for (var member : teams.membersOf(teamId)) {
                    teams.removeMember(member.accountId());
                }
                teams.deleteTeam(teamId);
            }
            scrubAllMemberships();
            // Clean ephemeral payee accounts created for auction payment tests.
            List<String> payees = new ArrayList<>(extraPayees);
            payees.add(amiraUsername);
            try (var connection = fixture.dataSource().getConnection()) {
                for (String username : payees) {
                    try (var deleteTx = connection.prepareStatement(
                            """
                            DELETE FROM wallet_transactions
                            WHERE source_account_id IN (SELECT id FROM accounts WHERE username = ?)
                               OR destination_account_id IN (SELECT id FROM accounts WHERE username = ?)
                               OR actor_account_id IN (SELECT id FROM accounts WHERE username = ?)
                            """)) {
                        deleteTx.setString(1, username);
                        deleteTx.setString(2, username);
                        deleteTx.setString(3, username);
                        deleteTx.executeUpdate();
                    }
                    try (var deleteWallet = connection.prepareStatement(
                            "DELETE FROM wallets WHERE account_id IN (SELECT id FROM accounts WHERE username = ?)")) {
                        deleteWallet.setString(1, username);
                        deleteWallet.executeUpdate();
                    }
                    try (var deleteIdentity = connection.prepareStatement(
                            """
                            DELETE FROM account_minecraft_identities
                            WHERE account_id IN (SELECT id FROM accounts WHERE username = ?)
                            """)) {
                        deleteIdentity.setString(1, username);
                        deleteIdentity.executeUpdate();
                    }
                    try (var deleteAccount = connection.prepareStatement(
                            "DELETE FROM accounts WHERE username = ?")) {
                        deleteAccount.setString(1, username);
                        deleteAccount.executeUpdate();
                    }
                }
            }
            fixture.close();
        }
    }
}
