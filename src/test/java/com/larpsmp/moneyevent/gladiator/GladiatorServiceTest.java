package com.larpsmp.moneyevent.gladiator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.SplitRewardResult;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import com.larpsmp.moneyevent.wallet.TransactionStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GladiatorServiceTest {

    @Test
    void openBuildsSixBoutScheduleAndDisablesKillMoney() throws Exception {
        try (GladiatorFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            assertTrue(fixture.service().open().success());
            GladiatorSession session = fixture.service().activeSession().orElseThrow();
            assertEquals(GladiatorPhase.OPEN, session.phase());
            assertEquals(6, session.schedule().size());
            assertFalse(fixture.money().isKillMoneyEnabledForVictim(setup.blueFighters().getFirst()));
        }
    }

    @Test
    void teamFightScoresOnceAndRespectsQuota() throws Exception {
        try (GladiatorFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.saveTeamKit();
            assertTrue(fixture.service().open().success());

            assertTrue(fixture.service().setLineup(setup.blue().id(), setup.blueFighters()).success());
            assertTrue(fixture.service().setLineup(setup.red().id(), setup.redFighters()).success());
            GladiatorService.AdminResult started = fixture.service().startFight();
            assertTrue(started.success(), started.message());

            GladiatorSession session = fixture.service().activeSession().orElseThrow();
            assertEquals(GladiatorPhase.TEAM_FIGHT, session.phase());
            assertEquals(1, session.fightsUsed(setup.blueFighters().getFirst()));

            UUID blueVictim = setup.blueFighters().get(0);
            UUID blueVictim2 = setup.blueFighters().get(1);
            UUID blueVictim3 = setup.blueFighters().get(2);
            fixture.service().handleDeath(blueVictim);
            fixture.service().handleDeath(blueVictim2);
            fixture.service().handleDeath(blueVictim3);

            session = fixture.service().activeSession().orElseThrow();
            assertEquals(GladiatorPhase.OPEN, session.phase());
            assertEquals(1, session.pointsByTeam().get(setup.red().id()));
            assertEquals(0, session.pointsByTeam().get(setup.blue().id()));
            assertEquals(GladiatorScheduleStatus.COMPLETED, session.schedule().getFirst().status());
        }
    }

    @Test
    void skipDoesNotConsumeQuota() throws Exception {
        try (GladiatorFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            assertTrue(fixture.service().open().success());
            assertTrue(fixture.service().skipFight().success());
            GladiatorSession session = fixture.service().activeSession().orElseThrow();
            assertEquals(GladiatorScheduleStatus.SKIPPED, session.schedule().getFirst().status());
            assertEquals(0, session.fightsUsed(setup.blueFighters().getFirst()));
        }
    }

    @Test
    void kitBanFlowStartsDuelAndScores() throws Exception {
        try (GladiatorFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.saveAllKits();
            assertTrue(fixture.service().open().success());
            // Finish schedule by skipping all bouts.
            for (int i = 0; i < 6; i++) {
                assertTrue(fixture.service().skipFight().success());
            }
            UUID a = setup.blueFighters().getFirst();
            UUID b = setup.redFighters().getFirst();
            assertTrue(fixture.service().nominateDuel(a, b).success());
            assertEquals(GladiatorPhase.KIT_BAN, fixture.service().activeSession().orElseThrow().phase());

            assertTrue(fixture.service().banKit(a, "1").success());
            assertTrue(fixture.service().banKit(b, "2").success());
            assertTrue(fixture.service().banKit(a, "3").success());
            assertTrue(fixture.service().banKit(b, "4").success());

            GladiatorSession session = fixture.service().activeSession().orElseThrow();
            assertEquals(GladiatorPhase.DUEL, session.phase());
            assertTrue(session.activeFight().isPresent());

            fixture.service().handleDeath(a);
            session = fixture.service().activeSession().orElseThrow();
            assertEquals(GladiatorPhase.OPEN, session.phase());
            assertEquals(1, session.pointsByTeam().get(setup.red().id()));
        }
    }

    @Test
    void payoutIsIdempotentIncludingZeroPlace() throws Exception {
        try (GladiatorFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.saveTeamKit();
            assertTrue(fixture.service().open().success());
            assertTrue(fixture.service().setLineup(setup.blue().id(), setup.blueFighters()).success());
            assertTrue(fixture.service().setLineup(setup.red().id(), setup.redFighters()).success());
            assertTrue(fixture.service().startFight().success());
            for (UUID accountId : setup.blueFighters()) {
                fixture.service().handleDeath(accountId);
            }
            for (int i = 0; i < 5; i++) {
                assertTrue(fixture.service().skipFight().success());
            }
            assertTrue(fixture.service().stop().success());

            GladiatorService.AdminResult first = fixture.service().payout();
            assertTrue(first.success(), first.message());
            GladiatorService.AdminResult second = fixture.service().payout();
            assertTrue(second.success(), second.message());
            assertTrue(second.message().contains("already"));

            // Direct API: $0 place is idempotent.
            SplitRewardResult zero = fixture.money().processGladiatorPrize(
                    fixture.service().activeSession().orElseThrow().sessionId().toString(),
                    setup.yellow().id(),
                    0L);
            assertEquals(TransactionStatus.SUCCESS, zero.status());
            SplitRewardResult zeroAgain = fixture.money().processGladiatorPrize(
                    fixture.service().activeSession().orElseThrow().sessionId().toString(),
                    setup.yellow().id(),
                    0L);
            assertEquals(TransactionStatus.SUCCESS, zeroAgain.status());
        }
    }

    @Test
    void restartRestoresPhaseScoreAndQuota() throws Exception {
        try (GladiatorFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.saveTeamKit();
            assertTrue(fixture.service().open().success());
            assertTrue(fixture.service().setLineup(setup.blue().id(), setup.blueFighters()).success());
            assertTrue(fixture.service().setLineup(setup.red().id(), setup.redFighters()).success());
            assertTrue(fixture.service().startFight().success());
            for (UUID accountId : setup.blueFighters()) {
                fixture.service().handleDeath(accountId);
            }
            UUID sessionId = fixture.service().activeSession().orElseThrow().sessionId();
            int redPoints = fixture.service().activeSession().orElseThrow()
                    .pointsByTeam().get(setup.red().id());
            int quota = fixture.service().activeSession().orElseThrow()
                    .fightsUsed(setup.blueFighters().getFirst());

            GladiatorService restored = fixture.reopenService();
            restored.restoreFromDatabase();
            GladiatorSession loaded = restored.activeSession().orElseThrow(
                    () -> new AssertionError("restore failed: " + fixture.errors));
            assertEquals(sessionId, loaded.sessionId());
            assertEquals(GladiatorPhase.OPEN, loaded.phase());
            assertEquals(redPoints, loaded.pointsByTeam().get(setup.red().id()));
            assertEquals(quota, loaded.fightsUsed(setup.blueFighters().getFirst()));
        }
    }

    @Test
    void stopClearsKillMoneyParticipants() throws Exception {
        try (GladiatorFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            assertTrue(fixture.service().open().success());
            assertFalse(fixture.money().isKillMoneyEnabledForVictim(setup.blueFighters().getFirst()));
            assertTrue(fixture.service().stop().success());
            // After stop, participants cleared — global kill money remains default (true in tests).
            assertTrue(fixture.money().isKillMoneyEnabledForVictim(setup.blueFighters().getFirst()));
        }
    }

    private static GladiatorFixture open() throws Exception {
        TestDatabaseSupport.Fixture moneyFixture = TestDatabaseSupport.open("GladiatorServiceTest");
        assumeTrue(moneyFixture != null, "Postgres unavailable");
        return new GladiatorFixture(moneyFixture);
    }

    private record TeamSetup(
            Team blue,
            Team red,
            Team yellow,
            List<UUID> blueFighters,
            List<UUID> redFighters,
            List<UUID> yellowFighters
    ) {
    }

    private static final class GladiatorFixture implements AutoCloseable {
        private final TestDatabaseSupport.Fixture fixture;
        private final TeamService teams;
        private final JdbcGladiatorRepository repository;
        private final AtomicReference<Instant> now;
        private final Clock clock;
        private GladiatorConfig config;
        private GladiatorService service;
        private final List<UUID> createdTeams = new ArrayList<>();
        private final OnlineWorld world = new OnlineWorld();
        private final List<String> errors = new ArrayList<>();

        GladiatorFixture(TestDatabaseSupport.Fixture fixture) throws Exception {
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
            this.repository = new JdbcGladiatorRepository(fixture.dataSource());
            this.now = new AtomicReference<>(Instant.parse("2026-11-26T12:00:00Z"));
            this.clock = new Clock() {
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
                    return now.get();
                }
            };
            this.config = readyConfig();
            this.service = new GladiatorService(
                    config, repository, fixture.money(), teams, clock, errors::add);
            this.service.setWorldActions(world);
            scrub();
        }

        GladiatorService service() {
            return service;
        }

        MoneyService money() {
            return fixture.money();
        }

        GladiatorService reopenService() {
            this.service = new GladiatorService(
                    config, repository, fixture.money(), teams, clock, errors::add);
            this.service.setWorldActions(world);
            return service;
        }

        TeamSetup seedThreeTeams() throws Exception {
            Team blue = createTeam("GladBlue", TeamColor.BLUE);
            Team red = createTeam("GladRed", TeamColor.RED);
            Team yellow = createTeam("GladYellow", TeamColor.YELLOW);
            List<UUID> blueFighters = List.of(
                    fillMember(blue, "B1"), fillMember(blue, "B2"), fillMember(blue, "B3"), fillMember(blue, "B4"));
            List<UUID> redFighters = List.of(
                    fillMember(red, "R1"), fillMember(red, "R2"), fillMember(red, "R3"), fillMember(red, "R4"));
            List<UUID> yellowFighters = List.of(
                    fillMember(yellow, "Y1"), fillMember(yellow, "Y2"), fillMember(yellow, "Y3"),
                    fillMember(yellow, "Y4"));
            for (UUID accountId : blueFighters) {
                world.online.add(accountId);
            }
            for (UUID accountId : redFighters) {
                world.online.add(accountId);
            }
            for (UUID accountId : yellowFighters) {
                world.online.add(accountId);
            }
            // Keep arena/seats geometry and team pads already applied in createTeam.
            this.config = service.config();
            return new TeamSetup(
                    blue, red, yellow,
                    blueFighters.subList(0, 3),
                    redFighters.subList(0, 3),
                    yellowFighters.subList(0, 3));
        }

        void saveTeamKit() throws Exception {
            repository.upsertKit(new GladiatorKit(
                    GladiatorRules.TEAM_KIT_ID, new byte[] {1}, new byte[] {2}, new byte[] {3}, Instant.now()));
        }

        void saveAllKits() throws Exception {
            saveTeamKit();
            for (int i = 1; i <= 5; i++) {
                repository.upsertKit(new GladiatorKit(
                        GladiatorRules.duelKitId(i),
                        new byte[] {(byte) i},
                        new byte[] {(byte) i},
                        new byte[] {(byte) i},
                        Instant.now()));
            }
        }

        private GladiatorConfig readyConfig() {
            GladiatorCuboid arena = GladiatorCuboid.undefined().withCorners("world", 0, 64, 0, 20, 80, 20);
            GladiatorCuboid seats = GladiatorCuboid.undefined().withCorners("world", 30, 64, 30, 40, 70, 40);
            GladiatorCuboid barriers = GladiatorCuboid.undefined().withCorners("world", -1, 64, -1, 21, 64, 21);
            GladiatorSpawnPoint seatsSpawn = GladiatorSpawnPoint.undefined().at("world", 35.5, 65, 35.5, 0, 0);
            GladiatorConfig base = GladiatorConfig.defaults()
                    .withArena(arena)
                    .withSeats(seats)
                    .withBarriers(barriers)
                    .withSeatsSpawn(seatsSpawn)
                    .withDuelSpawns(
                            GladiatorSpawnPoint.undefined().at("world", 5.5, 65, 5.5, 0, 0),
                            GladiatorSpawnPoint.undefined().at("world", 15.5, 65, 15.5, 0, 0));
            for (String slug : List.of("gladblue", "gladred", "gladyellow", "blue", "red", "yellow")) {
                // team slugs are generated with random suffix; set pads after create via open checks.
            }
            return base;
        }

        private void applyTeamPads(Team team, int offset) {
            GladiatorConfig current = service.config();
            for (int slot = 1; slot <= 3; slot++) {
                current = current.withTeamSpawn(
                        team.slug(),
                        slot,
                        GladiatorSpawnPoint.undefined().at(
                                "world", offset + slot + 0.5, 65, offset + 0.5, 0, 0));
            }
            service.replaceConfig(current);
            this.config = current;
        }

        private Team createTeam(String name, TeamColor color) {
            var result = teams.createTeam(name + "-" + UUID.randomUUID().toString().substring(0, 6), color);
            assertTrue(result.success(), result.error());
            createdTeams.add(result.team().id());
            applyTeamPads(result.team(), color == TeamColor.BLUE ? 0 : color == TeamColor.RED ? 10 : 20);
            return result.team();
        }

        private UUID fillMember(Team team, String prefix) throws Exception {
            TestDatabaseSupport.RegisteredPlayer player = fixture.register(prefix + UUID.randomUUID()
                    .toString().substring(0, 4));
            Account account = fixture.accounts().findByUsername(player.username()).orElseThrow();
            assertTrue(teams.assignMember(account.id(), team.id()).success());
            assertTrue(teams.setTier(account.id(), PlayerTier.TIER_3).success());
            return account.id();
        }

        private void scrub() throws Exception {
            try (var connection = fixture.dataSource().getConnection();
                 var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM gladiator_fighters");
                statement.executeUpdate("DELETE FROM gladiator_fights");
                statement.executeUpdate("DELETE FROM gladiator_lineups");
                statement.executeUpdate("DELETE FROM gladiator_quota");
                statement.executeUpdate("DELETE FROM gladiator_kit_bans");
                statement.executeUpdate("DELETE FROM gladiator_duel_pending");
                statement.executeUpdate("DELETE FROM gladiator_body_snapshots");
                statement.executeUpdate("DELETE FROM gladiator_schedule");
                statement.executeUpdate("DELETE FROM gladiator_barrier_blocks");
                statement.executeUpdate("DELETE FROM gladiator_kits");
                statement.executeUpdate("UPDATE gladiator_runtime SET active_session_id = NULL");
                statement.executeUpdate("DELETE FROM gladiator_sessions");
                // Isolate from leftover roster memberships on shared Postgres.
                statement.executeUpdate("DELETE FROM team_memberships");
                statement.executeUpdate("DELETE FROM competitor_tiers");
            }
        }

        @Override
        public void close() throws Exception {
            scrub();
            for (UUID teamId : createdTeams) {
                for (var member : teams.membersOf(teamId)) {
                    teams.removeMember(member.accountId());
                }
                teams.deleteTeam(teamId);
            }
            fixture.close();
        }
    }

    private static final class OnlineWorld implements GladiatorWorldActions {
        private final java.util.Set<UUID> online = new java.util.HashSet<>();

        @Override
        public boolean isOnline(UUID accountId) {
            return online.contains(accountId);
        }

        @Override
        public java.util.Optional<GladiatorBodySnapshot> captureBody(UUID accountId) {
            return java.util.Optional.of(new GladiatorBodySnapshot(
                    accountId,
                    "world",
                    0, 64, 0, 0, 0,
                    new byte[0], new byte[0], new byte[0], new byte[0],
                    20, 20, 5, 0, 0, 0, 0,
                    "SURVIVAL",
                    new byte[0]));
        }
    }
}
