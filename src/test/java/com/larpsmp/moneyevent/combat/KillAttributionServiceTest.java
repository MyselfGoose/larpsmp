package com.larpsmp.moneyevent.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.EconomyRules;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class KillAttributionServiceTest {

    @Test
    void attributedEnemyKillSettlesOnce() throws Exception {
        try (CombatFixture fixture = open()) {
            Team victims = fixture.createTeam("AttrVictims", TeamColor.YELLOW);
            Team killers = fixture.createTeam("AttrKillers", TeamColor.GREEN);
            TestDatabaseSupport.RegisteredPlayer victim = fixture.registerAssignTier(
                    victims, "AttrVictim", PlayerTier.TIER_2);
            fixture.registerAssignTier(victims, "AttrMateA", PlayerTier.TIER_3);
            fixture.registerAssignTier(victims, "AttrMateB", PlayerTier.TIER_3);
            fixture.registerAssignTier(victims, "AttrMateC", PlayerTier.TIER_3);
            TestDatabaseSupport.RegisteredPlayer killer = fixture.registerAssignTier(
                    killers, "AttrKiller", PlayerTier.TIER_1);
            fixture.registerAssignTier(killers, "AttrK2", PlayerTier.TIER_1);
            fixture.registerAssignTier(killers, "AttrK3", PlayerTier.TIER_1);
            fixture.registerAssignTier(killers, "AttrK4", PlayerTier.TIER_1);

            long killerBefore = fixture.money().balance(killer.minecraftUuid()).balance();
            Instant now = Instant.parse("2026-06-01T12:00:00Z");
            fixture.clock().set(now);

            AttributionOutcome outcome = fixture.attribution().attributeDeathWithId(
                    "death-attr-1",
                    victim.minecraftUuid(),
                    killer.minecraftUuid(),
                    AttributionCause.MELEE,
                    now.minusSeconds(8),
                    null);

            assertTrue(outcome.attributed());
            assertNotNull(outcome.settlement());
            assertEquals(EconomyRules.OUTCOME_APPLIED, outcome.settlement().outcome());
            assertEquals(30L, outcome.settlement().chargedTotal());
            assertEquals(killerBefore + 30L, fixture.money().balance(killer.minecraftUuid()).balance());
            assertFalse(outcome.killRecord().eventKill());
            assertFalse(outcome.killRecord().friendlyFire());
            assertTrue(outcome.killRecord().evidence().contains("melee"));
            assertTrue(fixture.kills().findByDeathId("death-attr-1").isPresent());

            AttributionOutcome replay = fixture.attribution().attributeDeathWithId(
                    "death-attr-1",
                    victim.minecraftUuid(),
                    killer.minecraftUuid(),
                    AttributionCause.MELEE,
                    now.minusSeconds(8),
                    null);
            assertEquals(AttributionOutcome.Kind.DUPLICATE, replay.kind());
            assertEquals(killerBefore + 30L, fixture.money().balance(killer.minecraftUuid()).balance());
        }
    }

    @Test
    void expiredAttackIsEnvironmental() throws Exception {
        try (CombatFixture fixture = open()) {
            Team victims = fixture.createTeam("ExpVictims", TeamColor.BLUE);
            Team killers = fixture.createTeam("ExpKillers", TeamColor.RED);
            TestDatabaseSupport.RegisteredPlayer victim = fixture.registerAssignTier(
                    victims, "ExpVictim", PlayerTier.TIER_1);
            fixture.fillTeam(victims, "ExpV", 3);
            TestDatabaseSupport.RegisteredPlayer killer = fixture.registerAssignTier(
                    killers, "ExpKiller", PlayerTier.TIER_1);
            fixture.fillTeam(killers, "ExpK", 3);

            Instant now = Instant.parse("2026-06-01T12:00:00Z");
            fixture.clock().set(now);
            long killerBefore = fixture.money().balance(killer.minecraftUuid()).balance();

            AttributionOutcome outcome = fixture.attribution().attributeDeathWithId(
                    "death-exp-1",
                    victim.minecraftUuid(),
                    killer.minecraftUuid(),
                    AttributionCause.MELEE,
                    now.minusSeconds(61),
                    null);

            assertEquals(AttributionOutcome.Kind.ENVIRONMENTAL, outcome.kind());
            assertEquals(killerBefore, fixture.money().balance(killer.minecraftUuid()).balance());
            assertTrue(fixture.kills().findByDeathId("death-exp-1").isEmpty());
        }
    }

    @Test
    void memoryPathAttributesWithinWindow() throws Exception {
        try (CombatFixture fixture = open()) {
            Team victims = fixture.createTeam("MemVictims", TeamColor.YELLOW);
            Team killers = fixture.createTeam("MemKillers", TeamColor.GREEN);
            TestDatabaseSupport.RegisteredPlayer victim = fixture.registerAssignTier(
                    victims, "MemVictim", PlayerTier.TIER_4);
            fixture.fillTeam(victims, "MemV", 3);
            TestDatabaseSupport.RegisteredPlayer killer = fixture.registerAssignTier(
                    killers, "MemKiller", PlayerTier.TIER_2);
            fixture.fillTeam(killers, "MemK", 3);

            Instant now = Instant.parse("2026-06-01T15:00:00Z");
            fixture.clock().set(now);
            fixture.memory().record(
                    victim.minecraftUuid(),
                    new AttackRecord(
                            killer.minecraftUuid(),
                            AttributionCause.PROJECTILE,
                            now.minusSeconds(5),
                            "ARROW"));

            AttributionOutcome outcome = fixture.attribution().attributeDeath(
                    victim.minecraftUuid(), AttributionCause.FALL_AFTER_HIT);

            assertTrue(outcome.attributed());
            assertEquals(EconomyRules.OUTCOME_APPLIED, outcome.settlement().outcome());
            assertEquals(AttributionCause.FALL_AFTER_HIT, outcome.killRecord().cause());
            assertEquals(15L, outcome.settlement().chargedTotal());
            assertTrue(fixture.memory().lastAttacker(victim.minecraftUuid()).isEmpty());
        }
    }

    @Test
    void friendlyFireAttributesButNoMoney() throws Exception {
        try (CombatFixture fixture = open()) {
            Team team = fixture.createTeam("FFCombat", TeamColor.RED);
            TestDatabaseSupport.RegisteredPlayer victim = fixture.registerAssignTier(
                    team, "FFCVictim", PlayerTier.TIER_1);
            TestDatabaseSupport.RegisteredPlayer killer = fixture.registerAssignTier(
                    team, "FFCKiller", PlayerTier.TIER_1);
            fixture.fillTeam(team, "FFC", 2);

            Instant now = Instant.parse("2026-06-01T16:00:00Z");
            fixture.clock().set(now);
            long victimBefore = fixture.money().balance(victim.minecraftUuid()).balance();
            long killerBefore = fixture.money().balance(killer.minecraftUuid()).balance();

            AttributionOutcome outcome = fixture.attribution().attributeDeathWithId(
                    "death-ff-combat-1",
                    victim.minecraftUuid(),
                    killer.minecraftUuid(),
                    AttributionCause.MELEE,
                    now.minusSeconds(2),
                    null);

            assertTrue(outcome.attributed());
            assertEquals(EconomyRules.OUTCOME_SKIPPED_FRIENDLY_FIRE, outcome.settlement().outcome());
            assertTrue(outcome.killRecord().friendlyFire());
            assertEquals(victimBefore, fixture.money().balance(victim.minecraftUuid()).balance());
            assertEquals(killerBefore, fixture.money().balance(killer.minecraftUuid()).balance());
        }
    }

    @Test
    void eventCombatFlagStoredForFutureLeaderboard() throws Exception {
        try (CombatFixture fixture = open()) {
            AtomicBoolean event = new AtomicBoolean(true);
            fixture.setEventScope((victim, killer) -> event.get());

            Team victims = fixture.createTeam("EvtVictims", TeamColor.BLUE);
            Team killers = fixture.createTeam("EvtKillers", TeamColor.YELLOW);
            TestDatabaseSupport.RegisteredPlayer victim = fixture.registerAssignTier(
                    victims, "EvtVictim", PlayerTier.TIER_3);
            fixture.fillTeam(victims, "EvtV", 3);
            TestDatabaseSupport.RegisteredPlayer killer = fixture.registerAssignTier(
                    killers, "EvtKiller", PlayerTier.TIER_3);
            fixture.fillTeam(killers, "EvtK", 3);

            Instant now = Instant.parse("2026-06-01T17:00:00Z");
            fixture.clock().set(now);

            AttributionOutcome outcome = fixture.attribution().attributeDeathWithId(
                    "death-evt-1",
                    victim.minecraftUuid(),
                    killer.minecraftUuid(),
                    AttributionCause.TRAP,
                    now.minusSeconds(1),
                    "DISPENSER");

            assertTrue(outcome.attributed());
            assertTrue(outcome.killRecord().eventKill());
            UUID killerAccountId = fixture.accountId(killer);
            assertTrue(fixture.kills().normalKillLeaderboard(10).stream()
                    .noneMatch(row -> row.killerAccountId().equals(killerAccountId)));
        }
    }

    @Test
    void missingAttackerIsEnvironmental() throws Exception {
        try (CombatFixture fixture = open()) {
            Team victims = fixture.createTeam("NoHitVictims", TeamColor.GREEN);
            TestDatabaseSupport.RegisteredPlayer victim = fixture.registerAssignTier(
                    victims, "NoHitVictim", PlayerTier.TIER_2);
            fixture.fillTeam(victims, "NoHitV", 3);

            AttributionOutcome outcome = fixture.attribution().attributeDeath(
                    victim.minecraftUuid(), null);
            assertEquals(AttributionOutcome.Kind.ENVIRONMENTAL, outcome.kind());
        }
    }

    private static CombatFixture open() throws Exception {
        TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("KillAttributionServiceTest");
        assumeTrue(fixture != null, "No reachable Postgres for combat attribution tests");
        return new CombatFixture(fixture);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class CombatFixture implements AutoCloseable {
        private final TestDatabaseSupport.Fixture fixture;
        private final TeamService teams;
        private final List<UUID> createdTeams = new ArrayList<>();
        private final MutableClock clock = new MutableClock();
        private final AttackMemory memory = new AttackMemory(CombatRules.ATTRIBUTION_WINDOW, clock);
        private final AccountSessionManager sessions = new AccountSessionManager();
        private final JdbcCombatKillRepository kills;
        private KillAttributionService attribution;
        private EventCombatScope eventScope = EventCombatScope.NEVER;

        CombatFixture(TestDatabaseSupport.Fixture fixture) throws Exception {
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
            this.kills = new JdbcCombatKillRepository(fixture.dataSource());
            rebuildAttribution();
        }

        void setEventScope(EventCombatScope scope) {
            this.eventScope = scope;
            rebuildAttribution();
        }

        private void rebuildAttribution() {
            this.attribution = new KillAttributionService(
                    memory,
                    sessions,
                    fixture.accounts(),
                    teams,
                    fixture.money(),
                    kills,
                    eventScope,
                    clock,
                    message -> {
                    });
        }

        KillAttributionService attribution() {
            return attribution;
        }

        AttackMemory memory() {
            return memory;
        }

        MutableClock clock() {
            return clock;
        }

        MoneyService money() {
            return fixture.money();
        }

        JdbcCombatKillRepository kills() {
            return kills;
        }

        Team createTeam(String name, TeamColor color) {
            var result = teams.createTeam(name + "-" + UUID.randomUUID().toString().substring(0, 6), color);
            assertTrue(result.success(), result.error());
            createdTeams.add(result.team().id());
            return result.team();
        }

        TestDatabaseSupport.RegisteredPlayer registerAssignTier(
                Team team,
                String name,
                PlayerTier tier
        ) throws Exception {
            TestDatabaseSupport.RegisteredPlayer player = fixture.register(name);
            Account account = fixture.accounts().findByUsername(player.username()).orElseThrow();
            assertTrue(teams.assignMember(account.id(), team.id()).success());
            assertTrue(teams.setTier(account.id(), tier).success());
            return player;
        }

        void fillTeam(Team team, String prefix, int count) throws Exception {
            for (int i = 1; i <= count; i++) {
                registerAssignTier(team, prefix + "Fill" + i, PlayerTier.TIER_4);
            }
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
