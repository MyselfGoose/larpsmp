package com.larpsmp.moneyevent.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EconomySettlementTest {

    @Test
    void splitRewardUsesRotatingRemainderAndSurvivesRestart() throws Exception {
        try (SettlementFixture fixture = open()) {
            Team team = fixture.createTeam("SplitBlue", TeamColor.BLUE);
            List<TestDatabaseSupport.RegisteredPlayer> members = fixture.enrollFour(team, "Split");

            SplitRewardResult first = fixture.money().splitReward(
                    team.id(),
                    EconomyRules.CAPTURE_REWARD,
                    TransactionType.CAPTURE_REWARD,
                    "capture-1",
                    EconomyRules.SETTLEMENT_KIND_CAPTURE,
                    "capture-rot-1");
            assertTrue(first.successful());
            assertEquals(EconomyRules.OUTCOME_APPLIED, first.outcome());
            List<Long> firstShares = first.shares().stream()
                    .map(SettlementContribution::amount)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            assertEquals(List.of(13L, 13L, 12L, 12L), firstShares);
            assertEquals(2, first.rotationIndexAfter());

            long totalAfterFirst = members.stream()
                    .mapToLong(player -> fixture.money().balance(player.minecraftUuid()).balance())
                    .sum();
            assertEquals(800L + 50L, totalAfterFirst);

            try (MoneyService reopened = new MoneyService(
                    fixture.accounts(), fixture.moneyRepository(), message -> { })) {
                reopened.setTeamService(fixture.teams());
                assertEquals(2, reopened.killMoneyStatus().rewardRotationIndex());

                SplitRewardResult second = reopened.splitReward(
                        team.id(),
                        EconomyRules.CAPTURE_REWARD,
                        TransactionType.CAPTURE_REWARD,
                        "capture-2",
                        EconomyRules.SETTLEMENT_KIND_CAPTURE,
                        "capture-rot-2");
                assertTrue(second.successful());
                assertEquals(2, second.rotationIndexBefore());
                assertEquals(0, second.rotationIndexAfter());
            }
        }
    }

    @Test
    void processCaptureRewardIsIdempotent() throws Exception {
        try (SettlementFixture fixture = open()) {
            Team team = fixture.createTeam("CapTeam", TeamColor.RED);
            fixture.enrollFour(team, "Cap");

            SplitRewardResult first = fixture.money().processCaptureReward("cap-dup-1", team.id());
            assertTrue(first.successful());
            assertEquals(50L, first.amount());

            long wealth = fixture.money().teamWealth(team.id());
            SplitRewardResult second = fixture.money().processCaptureReward("cap-dup-1", team.id());
            assertTrue(second.successful());
            assertEquals(wealth, fixture.money().teamWealth(team.id()));
            assertEquals(first.parentTransactionId(), second.parentTransactionId());
        }
    }

    @Test
    void deathShortfallCoverIncludesOfflineTeammates() throws Exception {
        try (SettlementFixture fixture = open()) {
            Team victims = fixture.createTeam("Victims", TeamColor.YELLOW);
            Team killers = fixture.createTeam("Killers", TeamColor.GREEN);

            TestDatabaseSupport.RegisteredPlayer victim = fixture.registerAndAssign(victims, "VictimPoor");
            TestDatabaseSupport.RegisteredPlayer mateA = fixture.registerAndAssign(victims, "MateA");
            TestDatabaseSupport.RegisteredPlayer mateB = fixture.registerAndAssign(victims, "MateB");
            TestDatabaseSupport.RegisteredPlayer mateC = fixture.registerAndAssign(victims, "MateC");
            TestDatabaseSupport.RegisteredPlayer killer = fixture.registerAndAssign(killers, "Killer");

            // Victim nearly broke; teammates (never "online") cover shortfall.
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().set(victim.minecraftUuid(), 5, "setup", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().set(mateA.minecraftUuid(), 100, "setup", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().set(mateB.minecraftUuid(), 100, "setup", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().set(mateC.minecraftUuid(), 100, "setup", null).status());

            long killerBefore = fixture.money().balance(killer.minecraftUuid()).balance();
            DeathPaymentResult result = fixture.money().processDeathPayment(new DeathPaymentRequest(
                    "death-shortfall-1",
                    fixture.accountId(victim),
                    fixture.accountId(killer),
                    PlayerTier.TIER_1,
                    false,
                    "melee"));
            assertTrue(result.applied());
            assertEquals(40L, result.owedAmount());
            assertEquals(40L, result.chargedTotal());
            assertFalse(result.rescueActivated());
            assertEquals(0L, fixture.money().balance(victim.minecraftUuid()).balance());
            assertEquals(killerBefore + 40L, fixture.money().balance(killer.minecraftUuid()).balance());

            long matesPaid = 100L - fixture.money().balance(mateA.minecraftUuid()).balance()
                    + 100L - fixture.money().balance(mateB.minecraftUuid()).balance()
                    + 100L - fixture.money().balance(mateC.minecraftUuid()).balance();
            assertEquals(35L, matesPaid);
        }
    }

    @Test
    void rescueTriggersBeforeUnaffordableDeathAndIsNotRecursive() throws Exception {
        try (SettlementFixture fixture = open()) {
            Team poor = fixture.createTeam("PoorTeam", TeamColor.BLUE);
            Team rich = fixture.createTeam("RichTeam", TeamColor.RED);
            Team other = fixture.createTeam("OtherTeam", TeamColor.YELLOW);

            TestDatabaseSupport.RegisteredPlayer victim = fixture.registerAndAssign(poor, "PoorVictim");
            TestDatabaseSupport.RegisteredPlayer p2 = fixture.registerAndAssign(poor, "Poor2");
            TestDatabaseSupport.RegisteredPlayer p3 = fixture.registerAndAssign(poor, "Poor3");
            TestDatabaseSupport.RegisteredPlayer p4 = fixture.registerAndAssign(poor, "Poor4");
            TestDatabaseSupport.RegisteredPlayer killer = fixture.registerAndAssign(rich, "RichKiller");
            List<TestDatabaseSupport.RegisteredPlayer> richMates = new ArrayList<>();
            richMates.add(fixture.registerAndAssign(rich, "Rich2"));
            richMates.add(fixture.registerAndAssign(rich, "Rich3"));
            richMates.add(fixture.registerAndAssign(rich, "Rich4"));
            List<TestDatabaseSupport.RegisteredPlayer> otherMembers = fixture.enrollFour(other, "Other");

            // Team wealth $50 → death $40 would leave $10 < $40 → rescue first.
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().set(victim.minecraftUuid(), 20, "setup", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().set(p2.minecraftUuid(), 10, "setup", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().set(p3.minecraftUuid(), 10, "setup", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    fixture.money().set(p4.minecraftUuid(), 10, "setup", null).status());

            long otherBefore = otherMembers.stream()
                    .mapToLong(player -> fixture.money().balance(player.minecraftUuid()).balance())
                    .sum();

            DeathPaymentResult result = fixture.money().processDeathPayment(new DeathPaymentRequest(
                    "death-rescue-1",
                    fixture.accountId(victim),
                    fixture.accountId(killer),
                    PlayerTier.TIER_1,
                    false,
                    "trap"));
            assertTrue(result.applied());
            assertTrue(result.rescueActivated());
            assertNotNull(result.rescueParentTransactionId());
            assertEquals(40L, result.chargedTotal());

            // Each other-team member gained $25 from rescue.
            long otherAfter = otherMembers.stream()
                    .mapToLong(player -> fixture.money().balance(player.minecraftUuid()).balance())
                    .sum();
            assertEquals(otherBefore + 100L, otherAfter);

            // Duplicate death id does not rescue or charge again.
            long killerBalance = fixture.money().balance(killer.minecraftUuid()).balance();
            DeathPaymentResult replay = fixture.money().processDeathPayment(new DeathPaymentRequest(
                    "death-rescue-1",
                    fixture.accountId(victim),
                    fixture.accountId(killer),
                    PlayerTier.TIER_1,
                    false,
                    "trap"));
            assertEquals(result.outcome(), replay.outcome());
            assertEquals(killerBalance, fixture.money().balance(killer.minecraftUuid()).balance());
        }
    }

    @Test
    void friendlyFireAndKillMoneyOffAreNoOps() throws Exception {
        try (SettlementFixture fixture = open()) {
            Team team = fixture.createTeam("FFTeam", TeamColor.GREEN);
            TestDatabaseSupport.RegisteredPlayer victim = fixture.registerAndAssign(team, "FFVictim");
            TestDatabaseSupport.RegisteredPlayer killer = fixture.registerAndAssign(team, "FFKiller");

            long victimBefore = fixture.money().balance(victim.minecraftUuid()).balance();
            long killerBefore = fixture.money().balance(killer.minecraftUuid()).balance();

            DeathPaymentResult friendly = fixture.money().processDeathPayment(new DeathPaymentRequest(
                    "death-ff-1",
                    fixture.accountId(victim),
                    fixture.accountId(killer),
                    PlayerTier.TIER_2,
                    true,
                    "friendly"));
            assertEquals(EconomyRules.OUTCOME_SKIPPED_FRIENDLY_FIRE, friendly.outcome());
            assertEquals(victimBefore, fixture.money().balance(victim.minecraftUuid()).balance());
            assertEquals(killerBefore, fixture.money().balance(killer.minecraftUuid()).balance());

            fixture.money().setKillMoneyGlobal(false, "test", "TEST", "unit");
            Team enemy = fixture.createTeam("EnemyFF", TeamColor.RED);
            TestDatabaseSupport.RegisteredPlayer enemyKiller = fixture.registerAndAssign(enemy, "EnemyK");
            DeathPaymentResult off = fixture.money().processDeathPayment(new DeathPaymentRequest(
                    "death-off-1",
                    fixture.accountId(victim),
                    fixture.accountId(enemyKiller),
                    PlayerTier.TIER_2,
                    false,
                    "off"));
            assertEquals(EconomyRules.OUTCOME_SKIPPED_KILL_MONEY_OFF, off.outcome());
            assertEquals(victimBefore, fixture.money().balance(victim.minecraftUuid()).balance());
            fixture.money().setKillMoneyGlobal(true, "test", "TEST", "restore");
        }
    }

    @Test
    void multiRescueAcrossDifferentDeathsIsAllowed() throws Exception {
        try (SettlementFixture fixture = open()) {
            Team poor = fixture.createTeam("MultiPoor", TeamColor.BLUE);
            Team hunters = fixture.createTeam("MultiHunt", TeamColor.RED);

            TestDatabaseSupport.RegisteredPlayer v1 = fixture.registerAndAssign(poor, "MP1");
            TestDatabaseSupport.RegisteredPlayer v2 = fixture.registerAndAssign(poor, "MP2");
            TestDatabaseSupport.RegisteredPlayer v3 = fixture.registerAndAssign(poor, "MP3");
            TestDatabaseSupport.RegisteredPlayer v4 = fixture.registerAndAssign(poor, "MP4");
            TestDatabaseSupport.RegisteredPlayer killer = fixture.enrollFour(hunters, "Hunt").getFirst();

            for (TestDatabaseSupport.RegisteredPlayer player : List.of(v1, v2, v3, v4)) {
                assertEquals(TransactionStatus.SUCCESS,
                        fixture.money().set(player.minecraftUuid(), 10, "setup", null).status());
            }

            DeathPaymentResult first = fixture.money().processDeathPayment(new DeathPaymentRequest(
                    "death-multi-1",
                    fixture.accountId(v1),
                    fixture.accountId(killer),
                    PlayerTier.TIER_4,
                    false,
                    "one"));
            assertTrue(first.rescueActivated());

            // Drain poor team again toward the threshold.
            for (TestDatabaseSupport.RegisteredPlayer player : List.of(v1, v2, v3, v4)) {
                assertEquals(TransactionStatus.SUCCESS,
                        fixture.money().set(player.minecraftUuid(), 10, "setup2", null).status());
            }
            DeathPaymentResult second = fixture.money().processDeathPayment(new DeathPaymentRequest(
                    "death-multi-2",
                    fixture.accountId(v2),
                    fixture.accountId(killer),
                    PlayerTier.TIER_4,
                    false,
                    "two"));
            assertTrue(second.rescueActivated());
            assertTrue(second.applied());
        }
    }

    private static SettlementFixture open() throws Exception {
        TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("EconomySettlementTest");
        assumeTrue(fixture != null, "No reachable Postgres for settlement tests");
        return new SettlementFixture(fixture);
    }

    private static final class SettlementFixture implements AutoCloseable {
        private final TestDatabaseSupport.Fixture fixture;
        private final TeamService teams;
        private final List<UUID> createdTeams = new ArrayList<>();

        SettlementFixture(TestDatabaseSupport.Fixture fixture) throws Exception {
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
            fixture.moneyRepository().updateKillMoneyGlobal(true);
            fixture.moneyRepository().updateTeamTotalsVisible(true);
            fixture.moneyRepository().updateRotationIndex(0);
        }

        MoneyService money() {
            return fixture.money();
        }

        com.larpsmp.moneyevent.auth.AccountRepository accounts() {
            return fixture.accounts();
        }

        JdbcMoneyRepository moneyRepository() {
            return fixture.moneyRepository();
        }

        TeamService teams() {
            return teams;
        }

        Team createTeam(String name, TeamColor color) {
            var result = teams.createTeam(name + "-" + UUID.randomUUID().toString().substring(0, 6), color);
            assertTrue(result.success(), result.error());
            createdTeams.add(result.team().id());
            return result.team();
        }

        List<TestDatabaseSupport.RegisteredPlayer> enrollFour(Team team, String prefix) throws Exception {
            List<TestDatabaseSupport.RegisteredPlayer> members = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                members.add(registerAndAssign(team, prefix + i));
            }
            return members;
        }

        TestDatabaseSupport.RegisteredPlayer registerAndAssign(Team team, String name) throws Exception {
            TestDatabaseSupport.RegisteredPlayer player = fixture.register(name);
            Account account = fixture.accounts().findByUsername(player.username()).orElseThrow();
            assertTrue(teams.assignMember(account.id(), team.id()).success());
            return player;
        }

        UUID accountId(TestDatabaseSupport.RegisteredPlayer player) throws Exception {
            return fixture.accountId(player);
        }

        @Override
        public void close() throws Exception {
            for (UUID teamId : createdTeams) {
                for (var member : teams.membersOf(teamId)) {
                    teams.removeMember(member.accountId());
                }
                teams.deleteTeam(teamId);
            }
            fixture.close();
        }
    }
}
