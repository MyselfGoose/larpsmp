package com.larpsmp.moneyevent.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import com.larpsmp.moneyevent.wallet.TransactionStatus;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HudStandingsSnapshotTest {

    @Test
    void ranksTeamsAndPlayersByWealthAndRespectsVisibility() throws Exception {
        TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("HudStandingsSnapshotTest");
        assumeTrue(fixture != null, "No reachable Postgres for HUD standings tests");
        try (fixture) {
            MoneyService money = fixture.money();
            TeamService teams = new TeamService(
                    new JdbcTeamRepository(fixture.dataSource()),
                    fixture.accounts(),
                    ignored -> { });
            money.setTeamService(teams);
            money.setTeamTotalsVisible(true, "test", "TEST", "hud-standings-setup");

            Team rich = teams.createTeam("HudRich" + hex(), TeamColor.BLUE).team();
            Team mid = teams.createTeam("HudMid" + hex(), TeamColor.RED).team();
            Team poor = teams.createTeam("HudPoor" + hex(), TeamColor.YELLOW).team();
            assertTrue(rich != null && mid != null && poor != null);

            TestDatabaseSupport.RegisteredPlayer a = fixture.registerCompetitor("HudA");
            TestDatabaseSupport.RegisteredPlayer b = fixture.registerCompetitor("HudB");
            TestDatabaseSupport.RegisteredPlayer c = fixture.registerCompetitor("HudC");
            UUID aId = fixture.accountId(a);
            UUID bId = fixture.accountId(b);
            UUID cId = fixture.accountId(c);

            assertTrue(teams.assignMember(aId, rich.id()).success());
            assertTrue(teams.assignMember(bId, mid.id()).success());
            assertTrue(teams.assignMember(cId, poor.id()).success());

            // High unique balances beat leftover wealth from other tests on seeded teams.
            assertEquals(TransactionStatus.SUCCESS,
                    money.set(a.minecraftUuid(), 50_000_000, "seed", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    money.set(b.minecraftUuid(), 40_000_000, "seed", null).status());
            assertEquals(TransactionStatus.SUCCESS,
                    money.set(c.minecraftUuid(), 30_000_000, "seed", null).status());

            HudStandingsSnapshot snapshot = new HudStandingsSnapshot(money, teams, ignored -> { });
            HudStandingsSnapshot.View view = snapshot.current();
            assertTrue(view.showTeamTotals());
            assertEquals(3, view.topTeams().size());
            assertEquals(rich.displayName(), view.topTeams().get(0).displayName());
            assertEquals(mid.displayName(), view.topTeams().get(1).displayName());
            assertEquals(poor.displayName(), view.topTeams().get(2).displayName());
            assertEquals(a.username(), view.topPlayers().get(0).username());
            assertEquals(50_000_000, view.topPlayers().get(0).balance());

            assertEquals(TransactionStatus.SUCCESS,
                    money.transfer(a.minecraftUuid(), b.minecraftUuid(), 15_000_000, "rebalance", null).status());
            snapshot.invalidate();
            HudStandingsSnapshot.View afterPay = snapshot.current();
            assertEquals(b.username(), afterPay.topPlayers().get(0).username());
            assertEquals(mid.displayName(), afterPay.topTeams().get(0).displayName());

            money.setTeamTotalsVisible(false, "test", "TEST", "hide");
            snapshot.invalidate();
            HudStandingsSnapshot.View hidden = snapshot.current();
            assertFalse(hidden.showTeamTotals());
            assertTrue(hidden.topTeams().isEmpty());
            assertFalse(hidden.topPlayers().isEmpty());

            HudBoardModel board = HudBoardModel.build(
                    hidden.showTeamTotals(),
                    hidden.topTeams(),
                    hidden.topPlayers(),
                    0L,
                    100L);
            List<String> lines = board.plainLines();
            assertFalse(lines.stream().anyMatch(line -> line.contains("TOP TEAMS")));
            assertTrue(lines.get(0).equals("TOP PLAYERS"));

            money.setTeamTotalsVisible(true, "test", "TEST", "restore");
            teams.deleteTeam(rich.id());
            teams.deleteTeam(mid.id());
            teams.deleteTeam(poor.id());
        }
    }

    private static String hex() {
        return Integer.toHexString(UUID.randomUUID().hashCode());
    }
}
