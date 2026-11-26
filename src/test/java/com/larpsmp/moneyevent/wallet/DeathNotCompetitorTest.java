package com.larpsmp.moneyevent.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeathNotCompetitorTest {

    @Test
    void deathPaymentSkipsWhenEitherSideIsNotCompetitor() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("DeathNotCompetitor")) {
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

            Team blue = teams.createTeam("DeathBlue" + hex(), TeamColor.BLUE).team();
            Team red = teams.createTeam("DeathRed" + hex(), TeamColor.RED).team();
            TestDatabaseSupport.RegisteredPlayer victim = fixture.register("DeathVictim");
            TestDatabaseSupport.RegisteredPlayer killer = fixture.register("DeathKiller");
            UUID victimId = fixture.accountId(victim);
            UUID killerId = fixture.accountId(killer);

            assertTrue(teams.assignMember(victimId, blue.id()).success());
            assertTrue(teams.assignMember(killerId, red.id()).success());
            assertTrue(teams.setTier(victimId, PlayerTier.TIER_4).success());
            assertTrue(teams.setParticipation(victimId, false, false).success());

            DeathPaymentResult result = money.processDeathPayment(new DeathPaymentRequest(
                    "death-not-competitor-" + hex(),
                    victimId,
                    killerId,
                    PlayerTier.TIER_4,
                    false,
                    "test"));
            assertEquals(EconomyRules.OUTCOME_SKIPPED_NOT_COMPETITOR, result.outcome());
            assertEquals(200L, money.accountByAccountId(victimId).orElseThrow().balance());
            assertEquals(200L, money.accountByAccountId(killerId).orElseThrow().balance());
        }
    }

    private static String hex() {
        return Integer.toHexString(UUID.randomUUID().hashCode());
    }
}
