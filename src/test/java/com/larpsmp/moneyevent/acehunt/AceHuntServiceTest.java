package com.larpsmp.moneyevent.acehunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AceHuntServiceTest {

    @Test
    void prepDoesNotScoreAceDeath() throws Exception {
        try (AceHuntFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.nominateAndCycle(setup);
            assertTrue(fixture.service().startPrep().success());

            AceHuntService.DeathHandlingResult result = fixture.service().handleDeath(
                    setup.blueAceAccountId(), null, null);
            assertInstanceOf(AceHuntService.DeathHandlingResult.AcePrepRespawn.class, result);

            AceHuntSession session = fixture.service().activeSession().orElseThrow();
            assertEquals(AceHuntPhase.PREP, session.phase());
            assertTrue(session.scoredDeaths().isEmpty());
            assertFalse(session.isSpectator(setup.blueAceAccountId()));
        }
    }

    @Test
    void assignedHuntKillAwardsPointsOnceAndSurvivesRestart() throws Exception {
        try (AceHuntFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.nominateAndCycle(setup);
            assertTrue(fixture.service().startPrep().success());
            assertTrue(fixture.service().beginHunt().success());

            AceHuntService.DeathHandlingResult first = fixture.service().handleDeath(
                    setup.redAceAccountId(),
                    "death-ace-1",
                    setup.blueHunterAccountId());
            assertInstanceOf(AceHuntService.DeathHandlingResult.Spectator.class, first);

            AceHuntSession session = fixture.service().activeSession().orElseThrow();
            assertEquals(1, session.scoredDeaths().size());
            assertTrue(session.scoredDeaths().getFirst().huntScored());
            assertTrue(session.isSpectator(setup.redAceAccountId()));
            assertTrue(session.isSpectator(setup.blueHunterAccountId()));

            AceHuntScoreboard board = AceHuntScoreboard.from(session);
            assertEquals(2, board.huntPoints().get(setup.blue().id()));

            AceHuntService.DeathHandlingResult duplicate = fixture.service().handleDeath(
                    setup.redAceAccountId(),
                    "death-ace-1-dup",
                    setup.blueHunterAccountId());
            assertInstanceOf(AceHuntService.DeathHandlingResult.Ignored.class, duplicate);
            assertEquals(1, fixture.service().activeSession().orElseThrow().scoredDeaths().size());

            UUID sessionId = session.sessionId();
            AceHuntService restored = fixture.reopenService();
            restored.restoreFromDatabase();
            AceHuntSession loaded = restored.activeSession().orElseThrow();
            assertEquals(sessionId, loaded.sessionId());
            assertEquals(AceHuntPhase.HUNT, loaded.phase());
            assertEquals(1, loaded.scoredDeaths().size());
            assertEquals(2, AceHuntScoreboard.from(loaded).huntPoints().get(setup.blue().id()));
        }
    }

    @Test
    void unassignedKillerDoesNotScoreHunt() throws Exception {
        try (AceHuntFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.nominateAndCycle(setup);
            assertTrue(fixture.service().startPrep().success());
            assertTrue(fixture.service().beginHunt().success());

            AceHuntService.DeathHandlingResult result = fixture.service().handleDeath(
                    setup.redAceAccountId(),
                    "death-env-1",
                    setup.yellowHunterAccountId());
            assertInstanceOf(AceHuntService.DeathHandlingResult.Spectator.class, result);

            AceHuntScoredDeath death = fixture.service().activeSession().orElseThrow()
                    .scoredDeaths().getFirst();
            assertFalse(death.huntScored());
            assertEquals(0, AceHuntScoreboard.from(fixture.service().activeSession().orElseThrow())
                    .huntPoints().get(setup.blue().id()));
        }
    }

    @Test
    void stopClearsSession() throws Exception {
        try (AceHuntFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.nominateAndCycle(setup);
            assertTrue(fixture.service().startPrep().success());
            assertTrue(fixture.service().beginHunt().success());
            assertFalse(fixture.money().isKillMoneyEnabledForVictim(setup.blueAceAccountId()));

            assertTrue(fixture.service().stop().success());
            assertTrue(fixture.service().activeSession().isEmpty());
        }
    }

    @Test
    void huntTimerAdvancesToSurfaceThenBan() throws Exception {
        try (AceHuntFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.nominateAndCycle(setup);
            assertTrue(fixture.service().startPrep().success());
            assertTrue(fixture.service().beginHunt().success());
            assertEquals(AceHuntPhase.HUNT, fixture.service().activeSession().orElseThrow().phase());

            fixture.advance(Duration.ofMinutes(18).plusSeconds(1));
            fixture.service().tick();
            assertEquals(AceHuntPhase.SURFACE, fixture.service().activeSession().orElseThrow().phase());

            fixture.advance(Duration.ofSeconds(61));
            fixture.service().tick();
            assertEquals(AceHuntPhase.BAN, fixture.service().activeSession().orElseThrow().phase());
        }
    }

    @Test
    void allAcesDeadEndsSession() throws Exception {
        try (AceHuntFixture fixture = open()) {
            TeamSetup setup = fixture.seedThreeTeams();
            fixture.nominateAndCycle(setup);
            assertTrue(fixture.service().startPrep().success());
            assertTrue(fixture.service().beginHunt().success());

            // Cycle: Blue→Red, Red→Yellow, Yellow→Blue
            fixture.service().handleDeath(setup.redAceAccountId(), "d1", setup.blueHunterAccountId());
            fixture.service().handleDeath(setup.yellowAceAccountId(), "d2", setup.redHunterAccountId());
            fixture.service().handleDeath(setup.blueAceAccountId(), "d3", setup.yellowHunterAccountId());

            assertTrue(fixture.service().activeSession().isEmpty());
        }
    }

    private static AceHuntFixture open() throws Exception {
        TestDatabaseSupport.Fixture moneyFixture = TestDatabaseSupport.open("AceHuntServiceTest");
        assumeTrue(moneyFixture != null, "Postgres unavailable");
        return new AceHuntFixture(moneyFixture);
    }

    private record TeamSetup(
            Team blue,
            Team red,
            Team yellow,
            UUID blueAceAccountId,
            UUID redAceAccountId,
            UUID yellowAceAccountId,
            UUID blueHunterAccountId,
            UUID redHunterAccountId,
            UUID yellowHunterAccountId
    ) {
    }

    private static final class AceHuntFixture implements AutoCloseable {
        private final TestDatabaseSupport.Fixture fixture;
        private final TeamService teams;
        private final JdbcAceHuntRepository repository;
        private final AtomicReference<Instant> now;
        private final Clock clock;
        private AceHuntConfig config;
        private AceHuntService service;
        private final List<UUID> createdTeams = new ArrayList<>();

        AceHuntFixture(TestDatabaseSupport.Fixture fixture) throws Exception {
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
            this.repository = new JdbcAceHuntRepository(fixture.dataSource());
            this.now = new AtomicReference<>(Instant.parse("2026-11-25T12:00:00Z"));
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
            this.config = AceHuntConfig.defaults();
            this.service = new AceHuntService(
                    config, repository, fixture.money(), teams, clock, message -> {
            });
            scrub();
        }

        AceHuntService service() {
            return service;
        }

        MoneyService money() {
            return fixture.money();
        }

        void advance(Duration duration) {
            now.set(now.get().plus(duration));
        }

        AceHuntService reopenService() {
            this.service = new AceHuntService(
                    config, repository, fixture.money(), teams, clock, message -> {
            });
            return service;
        }

        TeamSetup seedThreeTeams() throws Exception {
            Team blue = createTeam("AceBlue", TeamColor.BLUE);
            Team red = createTeam("AceRed", TeamColor.RED);
            Team yellow = createTeam("AceYellow", TeamColor.YELLOW);

            UUID blueAce = fillMember(blue, "BlueAce");
            UUID blueHunter = fillMember(blue, "BlueHunt");
            fillMember(blue, "Blue3");
            fillMember(blue, "Blue4");

            UUID redAce = fillMember(red, "RedAce");
            UUID redHunter = fillMember(red, "RedHunt");
            fillMember(red, "Red3");
            fillMember(red, "Red4");

            UUID yellowAce = fillMember(yellow, "YellowAce");
            UUID yellowHunter = fillMember(yellow, "YellowHunt");
            fillMember(yellow, "Yellow3");
            fillMember(yellow, "Yellow4");

            List<AceHuntTerritory> territories = List.of(
                    readyTerritory(blue, 0),
                    readyTerritory(red, 100),
                    readyTerritory(yellow, 200)
            );
            AceHuntCuboid lockup = AceHuntCuboid.undefined().withCorners("world", -10, 60, -10, -5, 65, -5);
            AceHuntSpawnPoint lockupSpawn = AceHuntSpawnPoint.undefined()
                    .at("world", -7.5, 61, -7.5, 0, 0);
            this.config = AceHuntConfig.defaults()
                    .withTerritories(territories)
                    .withLockup(lockup, lockupSpawn);
            this.service.replaceConfig(config);
            return new TeamSetup(
                    blue, red, yellow,
                    blueAce, redAce, yellowAce,
                    blueHunter, redHunter, yellowHunter);
        }

        void nominateAndCycle(TeamSetup setup) {
            assertTrue(service.nominateAce(setup.blue().id(), setup.blueAceAccountId()).success());
            assertTrue(service.nominateAce(setup.red().id(), setup.redAceAccountId()).success());
            assertTrue(service.nominateAce(setup.yellow().id(), setup.yellowAceAccountId()).success());
            // Explicit ring: Blue→Red, Red→Yellow, Yellow→Blue (do not rely on sort-order cycle).
            assertTrue(service.setHuntAssignment(setup.blue().id(), setup.red().id()).success());
            assertTrue(service.setHuntAssignment(setup.red().id(), setup.yellow().id()).success());
            assertTrue(service.setHuntAssignment(setup.yellow().id(), setup.blue().id()).success());
        }

        private AceHuntTerritory readyTerritory(Team team, int offset) {
            AceHuntCuboid region = AceHuntCuboid.undefined()
                    .withCorners("world", offset, 60, offset, offset + 20, 80, offset + 20);
            AceHuntSpawnPoint aceSpawn = AceHuntSpawnPoint.undefined()
                    .at("world", offset + 10.5, 65, offset + 10.5, 0, 0);
            AceHuntSpawnPoint hunterSpawn = AceHuntSpawnPoint.undefined()
                    .at("world", offset + 12.5, 65, offset + 12.5, 0, 0);
            return AceHuntTerritory.empty(team.id(), team.slug())
                    .withRegion(region)
                    .withAceSpawn(aceSpawn)
                    .withHunterSpawn(hunterSpawn)
                    .withSurfaceY(64)
                    .withMaxLegalY(75);
        }

        private Team createTeam(String name, TeamColor color) {
            var result = teams.createTeam(name + "-" + UUID.randomUUID().toString().substring(0, 6), color);
            assertTrue(result.success(), result.error());
            createdTeams.add(result.team().id());
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
                statement.executeUpdate("DELETE FROM ace_hunt_scored_deaths");
                statement.executeUpdate("DELETE FROM ace_hunt_lockouts");
                statement.executeUpdate("DELETE FROM ace_hunt_snapshots");
                statement.executeUpdate("DELETE FROM ace_hunt_spectators");
                statement.executeUpdate("DELETE FROM ace_hunt_assignments");
                statement.executeUpdate("DELETE FROM ace_hunt_aces");
                statement.executeUpdate("UPDATE ace_hunt_runtime SET active_session_id = NULL");
                statement.executeUpdate("DELETE FROM ace_hunt_sessions");
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
}
