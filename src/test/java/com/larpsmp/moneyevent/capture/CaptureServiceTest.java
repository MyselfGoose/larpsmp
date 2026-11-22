package com.larpsmp.moneyevent.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.EconomyRules;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.SplitRewardResult;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CaptureServiceTest {

    @Test
    void contestedCapturePaysExactlyOnceAndSurvivesRestart() throws Exception {
        try (CaptureFixture fixture = open()) {
            Team blue = fixture.createTeam("CapBlue", TeamColor.BLUE);
            fixture.fillTeam(blue, "CapB", 4);
            CapturePointDefinition point = fixture.definedPoint("quarry");

            CaptureService.StartResult started = fixture.service().start("quarry");
            assertInstanceOf(CaptureService.StartResult.Started.class, started);
            CaptureSession session = ((CaptureService.StartResult.Started) started).session();
            UUID captureId = session.captureId();

            long full = fixture.config().fullCaptureMillis(1);
            CaptureService.TickOutcome tick = fixture.service().tick(
                    CaptureAdvantage.fromHeadcounts(Map.of(blue.id(), 1)),
                    full + 1);
            assertEquals(CaptureService.TickOutcome.Kind.COMPLETED, tick.kind());
            assertEquals(CaptureSessionStatus.COMPLETED, tick.session().status());
            assertEquals(blue.id(), tick.session().winningTeamId());

            SplitRewardResult first = fixture.money().processCaptureReward(captureId.toString(), blue.id());
            assertTrue(first.successful());
            assertEquals(EconomyRules.OUTCOME_APPLIED, first.outcome());
            long blueTotal = fixture.teamBalance(blue);
            assertEquals(EconomyRules.CAPTURE_REWARD
                    + (MoneyService.STARTING_BALANCE * 4), blueTotal);

            // Restart: reload service from DB — session terminal, runtime cleared.
            CaptureService reopened = fixture.reopenService();
            assertTrue(reopened.activeSession().isEmpty());
            assertTrue(reopened.completedPointsToday().contains("quarry"));

            SplitRewardResult second = fixture.money().processCaptureReward(captureId.toString(), blue.id());
            assertTrue(second.successful());
            assertEquals(blueTotal, fixture.teamBalance(blue));
        }
    }

    @Test
    void pauseFreezesProgressAcrossTicks() throws Exception {
        try (CaptureFixture fixture = open()) {
            Team blue = fixture.createTeam("PauseBlue", TeamColor.BLUE);
            fixture.fillTeam(blue, "PauseB", 4);
            fixture.service().start("quarry");

            long full = fixture.config().fullCaptureMillis(1);
            fixture.service().tick(CaptureAdvantage.fromHeadcounts(Map.of(blue.id(), 1)), full / 4);
            long before = fixture.service().activeSession().orElseThrow().progress().progressMillis();

            assertInstanceOf(CaptureService.AdminResult.Ok.class, fixture.service().pause());
            fixture.service().tick(CaptureAdvantage.fromHeadcounts(Map.of(blue.id(), 1)), full / 4);
            long after = fixture.service().activeSession().orElseThrow().progress().progressMillis();
            assertEquals(before, after);

            assertInstanceOf(CaptureService.AdminResult.Ok.class, fixture.service().resume());
            fixture.service().tick(CaptureAdvantage.fromHeadcounts(Map.of(blue.id(), 1)), full / 4);
            assertTrue(fixture.service().activeSession().orElseThrow().progress().progressMillis() > before);
        }
    }

    @Test
    void dayCompletionPreventsReopeningSamePoint() throws Exception {
        try (CaptureFixture fixture = open()) {
            Team blue = fixture.createTeam("DayBlue", TeamColor.BLUE);
            fixture.fillTeam(blue, "DayB", 4);
            fixture.service().start("quarry");
            long full = fixture.config().fullCaptureMillis(1);
            fixture.service().tick(CaptureAdvantage.fromHeadcounts(Map.of(blue.id(), 1)), full + 1);
            // Completion auto-opens the next point; stop it so we can probe quarry again.
            fixture.service().stop();

            CaptureService.StartResult again = fixture.service().start("quarry");
            assertInstanceOf(CaptureService.StartResult.AlreadyCompleted.class, again);
        }
    }

    @Test
    void tempBlocksRoundTrip() throws Exception {
        try (CaptureFixture fixture = open()) {
            Instant now = Instant.parse("2026-07-01T12:00:00Z");
            CaptureTempBlock block = new CaptureTempBlock(
                    "world", 1, 64, 2, "minecraft:air", "STONE", now, now.plusSeconds(30));
            fixture.repository().upsertTempBlock(block);
            List<CaptureTempBlock> loaded = fixture.repository().listTempBlocks();
            assertEquals(1, loaded.size());
            assertEquals(block, loaded.getFirst());
            fixture.repository().deleteTempBlock("world", 1, 64, 2);
            assertTrue(fixture.repository().listTempBlocks().isEmpty());
        }
    }

    @Test
    void progressSurvivesServiceReload() throws Exception {
        try (CaptureFixture fixture = open()) {
            Team blue = fixture.createTeam("ReloadBlue", TeamColor.BLUE);
            fixture.fillTeam(blue, "ReloadB", 4);
            fixture.service().start("quarry");
            long full = fixture.config().fullCaptureMillis(1);
            fixture.service().tick(CaptureAdvantage.fromHeadcounts(Map.of(blue.id(), 1)), full / 5);
            UUID captureId = fixture.service().activeSession().orElseThrow().captureId();
            long progress = fixture.service().activeSession().orElseThrow().progress().progressMillis();
            fixture.service().flush();

            CaptureService reopened = fixture.reopenService();
            reopened.restoreFromDatabase();
            CaptureSession restored = reopened.activeSession().orElseThrow();
            assertEquals(captureId, restored.captureId());
            assertEquals(progress, restored.progress().progressMillis());
            assertEquals(blue.id(), restored.progress().owningTeamId());
            assertNotEquals(CaptureSessionStatus.COMPLETED, restored.status());
        }
    }

    private static CaptureFixture open() throws Exception {
        TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("CaptureServiceTest");
        assumeTrue(fixture != null, "No reachable Postgres for capture service tests");
        return new CaptureFixture(fixture);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-07-01T12:00:00Z");

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

    private static final class CaptureFixture implements AutoCloseable {
        private final TestDatabaseSupport.Fixture fixture;
        private final TeamService teams;
        private final List<UUID> createdTeams = new ArrayList<>();
        private final MutableClock clock = new MutableClock();
        private final JdbcCaptureRepository repository;
        private final CaptureConfig config;
        private CaptureService service;

        CaptureFixture(TestDatabaseSupport.Fixture fixture) throws Exception {
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
            this.repository = new JdbcCaptureRepository(fixture.dataSource());
            this.config = CaptureConfig.defaults().withPoints(List.of(
                    new CapturePointDefinition(
                            "quarry", "Quarry", "world", 0, 64, 0, 5, 64, 5),
                    new CapturePointDefinition(
                            "tower", "Tower", "world", 20, 70, 20, 25, 70, 25),
                    new CapturePointDefinition(
                            "ruins", "Ruins", "world", 40, 65, 40, 45, 65, 45)
            ));
            this.service = new CaptureService(
                    config, repository, fixture.money(), teams, clock, message -> {
            });
            scrubCaptureTables();
        }

        CaptureService service() {
            return service;
        }

        CaptureConfig config() {
            return config;
        }

        JdbcCaptureRepository repository() {
            return repository;
        }

        MoneyService money() {
            return fixture.money();
        }

        CaptureService reopenService() {
            this.service = new CaptureService(
                    config, repository, fixture.money(), teams, clock, message -> {
            });
            return service;
        }

        CapturePointDefinition definedPoint(String id) {
            return config.point(id).orElseThrow();
        }

        Team createTeam(String name, TeamColor color) {
            var result = teams.createTeam(name + "-" + UUID.randomUUID().toString().substring(0, 6), color);
            assertTrue(result.success(), result.error());
            createdTeams.add(result.team().id());
            return result.team();
        }

        void fillTeam(Team team, String prefix, int count) throws Exception {
            for (int i = 1; i <= count; i++) {
                TestDatabaseSupport.RegisteredPlayer player = fixture.register(prefix + i);
                Account account = fixture.accounts().findByUsername(player.username()).orElseThrow();
                assertTrue(teams.assignMember(account.id(), team.id()).success());
                assertTrue(teams.setTier(account.id(), PlayerTier.TIER_3).success());
            }
        }

        long teamBalance(Team team) throws Exception {
            return fixture.money().teamWealth(team.id());
        }

        private void scrubCaptureTables() throws Exception {
            try (var connection = fixture.dataSource().getConnection();
                 var statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM capture_day_completions");
                statement.executeUpdate("DELETE FROM capture_temp_blocks");
                statement.executeUpdate("UPDATE capture_runtime SET active_capture_id = NULL, paused = FALSE");
                statement.executeUpdate("DELETE FROM capture_sessions");
            }
        }

        @Override
        public void close() throws Exception {
            scrubCaptureTables();
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
