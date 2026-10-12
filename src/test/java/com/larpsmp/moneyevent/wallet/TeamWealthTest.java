package com.larpsmp.moneyevent.wallet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamMutationResult;
import com.larpsmp.moneyevent.team.TeamService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamWealthTest {

    @Test
    void sumsRosterWalletsIncludingStartingBalances() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("TeamWealthTest-sum")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TeamService teams = new TeamService(
                    new JdbcTeamRepository(fixture.dataSource()),
                    fixture.accounts(),
                    message -> {
                    });
            // Isolated team so leftover Blue/Red/Yellow roster from other tests cannot interfere.
            TeamMutationResult created = teams.createTeam("WealthProbe", TeamColor.GREEN);
            assertTrue(created.success());
            Team team = created.team();
            assertEquals(0L, fixture.money().teamWealth(team.id()));

            TestDatabaseSupport.RegisteredPlayer alpha = fixture.register("WealthA");
            TestDatabaseSupport.RegisteredPlayer beta = fixture.register("WealthB");
            Account alphaAccount = fixture.accounts().findByUsername(alpha.username()).orElseThrow();
            Account betaAccount = fixture.accounts().findByUsername(beta.username()).orElseThrow();

            assertTrue(teams.assignMember(alphaAccount.id(), team.id()).success());
            assertTrue(teams.assignMember(betaAccount.id(), team.id()).success());

            // Starting balance is $200 each → team wealth $400 before adjustments.
            assertEquals(400L, fixture.money().teamWealth(team.id()));

            TransactionResult add = fixture.money().add(
                    alpha.minecraftUuid(), 50, "test top-up", alpha.minecraftUuid());
            assertEquals(TransactionStatus.SUCCESS, add.status());
            assertEquals(450L, fixture.money().teamWealth(team.id()));

            teams.removeMember(alphaAccount.id());
            teams.removeMember(betaAccount.id());
            assertEquals(0L, fixture.money().teamWealth(team.id()));
            teams.deleteTeam(team.id());
        }
    }

    @Test
    void emptyTeamWealthIsZero() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("TeamWealthTest-empty")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            UUID missing = UUID.randomUUID();
            assertEquals(0L, fixture.money().teamWealth(missing));
        }
    }
}
