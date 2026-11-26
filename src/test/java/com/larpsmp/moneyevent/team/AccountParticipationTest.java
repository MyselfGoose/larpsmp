package com.larpsmp.moneyevent.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountParticipationTest {

    @Test
    void assignSetsCompetitorAndGrantsOnceUnsetClearsWithoutClawback() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("ParticipationAssign")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TeamService teams = new TeamService(
                    new JdbcTeamRepository(fixture.dataSource()),
                    fixture.accounts(),
                    message -> {
                    });
            MoneyService money = fixture.money();
            money.setTeamService(teams);
            teams.setCompetitorEnrollmentListener(accountId -> {
                try {
                    money.grantCompetitorStartingBalanceIfNeeded(accountId);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            Team team = teams.createTeam("RoleBlue" + hex(), TeamColor.BLUE).team();
            TestDatabaseSupport.RegisteredPlayer player = fixture.register("RolePlayer");
            UUID accountId = fixture.accountId(player);
            Account account = fixture.accounts().findById(accountId).orElseThrow();

            assertFalse(teams.isCompetitor(accountId));
            assertEquals(0L, money.accountByAccountId(accountId).orElseThrow().balance());

            assertTrue(teams.assignMember(accountId, team.id()).success());
            assertTrue(teams.isCompetitor(accountId));
            assertFalse(teams.isSpectator(accountId));
            assertEquals(200L, money.accountByAccountId(accountId).orElseThrow().balance());

            Team other = teams.createTeam("RoleRed" + hex(), TeamColor.RED).team();
            assertTrue(teams.assignMember(accountId, other.id()).success());
            assertTrue(teams.isCompetitor(accountId));
            assertEquals(200L, money.accountByAccountId(accountId).orElseThrow().balance());

            assertTrue(teams.removeMember(accountId).success());
            assertFalse(teams.isCompetitor(accountId));
            assertEquals(200L, money.accountByAccountId(accountId).orElseThrow().balance());

            List<WalletAccount> standings = money.competitorStandings();
            assertTrue(standings.stream().noneMatch(row -> row.accountId().equals(accountId)),
                    "Removed competitor must leave personal standings: " + account.username());
            assertEquals(0L, money.teamWealth(other.id()));
        }
    }

    @Test
    void spectatorAndCompetitorAreMutuallyExclusive() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("ParticipationRoles")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TeamService teams = new TeamService(
                    new JdbcTeamRepository(fixture.dataSource()),
                    fixture.accounts(),
                    message -> {
                    });
            MoneyService money = fixture.money();
            money.setTeamService(teams);
            teams.setCompetitorEnrollmentListener(accountId -> {
                try {
                    money.grantCompetitorStartingBalanceIfNeeded(accountId);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            TestDatabaseSupport.RegisteredPlayer player = fixture.register("SpecPlayer");
            UUID accountId = fixture.accountId(player);

            assertTrue(teams.setParticipation(accountId, true, false).success());
            assertTrue(teams.isCompetitor(accountId));
            assertEquals(200L, money.accountByAccountId(accountId).orElseThrow().balance());

            assertTrue(teams.setParticipation(accountId, false, true).success());
            assertFalse(teams.isCompetitor(accountId));
            assertTrue(teams.isSpectator(accountId));
            assertEquals(200L, money.accountByAccountId(accountId).orElseThrow().balance());
            assertTrue(money.competitorStandings().stream()
                    .noneMatch(row -> row.accountId().equals(accountId)));

            assertTrue(teams.setParticipation(accountId, false, false).success());
            assertFalse(teams.isCompetitor(accountId));
            assertFalse(teams.isSpectator(accountId));

            assertFalse(teams.setParticipation(accountId, true, true).success());
        }
    }

    @Test
    void teamWealthSumsOnlyCompetitors() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("ParticipationWealth")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TeamService teams = new TeamService(
                    new JdbcTeamRepository(fixture.dataSource()),
                    fixture.accounts(),
                    message -> {
                    });
            MoneyService money = fixture.money();
            money.setTeamService(teams);
            teams.setCompetitorEnrollmentListener(accountId -> {
                try {
                    money.grantCompetitorStartingBalanceIfNeeded(accountId);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });

            Team team = teams.createTeam("WealthRoles" + hex(), TeamColor.GREEN).team();
            TestDatabaseSupport.RegisteredPlayer competitor = fixture.register("WealthComp");
            TestDatabaseSupport.RegisteredPlayer nonCompetitor = fixture.register("WealthNon");
            UUID competitorId = fixture.accountId(competitor);
            UUID nonCompetitorId = fixture.accountId(nonCompetitor);

            assertTrue(teams.assignMember(competitorId, team.id()).success());
            assertTrue(teams.assignMember(nonCompetitorId, team.id()).success());
            // Force non-competitor while keeping roster membership.
            assertTrue(teams.setParticipation(nonCompetitorId, false, false).success());
            assertEquals(1, teams.competitorsOf(team.id()).size());
            assertEquals(200L, money.teamWealth(team.id()));
        }
    }

    private static String hex() {
        return Integer.toHexString(UUID.randomUUID().hashCode());
    }
}
