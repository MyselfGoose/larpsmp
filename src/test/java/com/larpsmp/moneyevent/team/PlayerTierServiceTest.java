package com.larpsmp.moneyevent.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlayerTierServiceTest {

    @Test
    void setClearPersistAndSurviveTeamMove() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("PlayerTierServiceTest-persist")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TeamService teams = service(fixture);
            TestDatabaseSupport.RegisteredPlayer player = fixture.register("TierPersist");
            Account account = fixture.accounts().findByUsername(player.username()).orElseThrow();
            Team blue = teams.findTeamByNameOrSlug("blue").orElseThrow();
            Team red = teams.findTeamByNameOrSlug("red").orElseThrow();

            assertTrue(teams.tierOf(account.id()).isEmpty());
            assertTrue(teams.setTier(account.id(), PlayerTier.TIER_2).success());
            assertEquals(Optional.of(PlayerTier.TIER_2), teams.tierOf(account.id()));

            assertTrue(teams.assignMember(account.id(), blue.id()).success());
            assertEquals(Optional.of(PlayerTier.TIER_2), teams.membershipOf(account.id()).flatMap(TeamMember::playerTier));

            assertTrue(teams.assignMember(account.id(), red.id()).success());
            assertEquals(Optional.of(PlayerTier.TIER_2), teams.tierOf(account.id()));
            assertEquals(Optional.of(PlayerTier.TIER_2), teams.membershipOf(account.id()).flatMap(TeamMember::playerTier));

            assertTrue(teams.clearTier(account.id()).success());
            assertTrue(teams.tierOf(account.id()).isEmpty());
            assertFalse(teams.clearTier(account.id()).success());

            teams.removeMember(account.id());
        }
    }

    @Test
    void warnsOnMissingAndDuplicateTiers() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("PlayerTierServiceTest-warn")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TeamService teams = service(fixture);
            Team blue = teams.findTeamByNameOrSlug("blue").orElseThrow();

            TestDatabaseSupport.RegisteredPlayer one = fixture.register("TierWarn1");
            TestDatabaseSupport.RegisteredPlayer two = fixture.register("TierWarn2");
            Account accountOne = fixture.accounts().findByUsername(one.username()).orElseThrow();
            Account accountTwo = fixture.accounts().findByUsername(two.username()).orElseThrow();

            assertTrue(teams.assignMember(accountOne.id(), blue.id()).success());
            assertTrue(teams.assignMember(accountTwo.id(), blue.id()).success());

            List<String> missing = teams.rosterWarnings();
            assertTrue(missing.stream().anyMatch(text -> text.contains("no tier")), missing::toString);

            assertTrue(teams.setTier(accountOne.id(), PlayerTier.TIER_1).success());
            assertTrue(teams.setTier(accountTwo.id(), PlayerTier.TIER_1).success());

            List<String> duplicate = teams.rosterWarnings();
            assertTrue(duplicate.stream().anyMatch(text ->
                            text.contains("Blue") && text.contains("Tier 1") && text.contains("2")),
                    duplicate::toString);

            teams.removeMember(accountOne.id());
            teams.removeMember(accountTwo.id());
            teams.clearTier(accountOne.id());
            teams.clearTier(accountTwo.id());
        }
    }

    @Test
    void fromTokenParsesCommonForms() {
        assertEquals(Optional.of(PlayerTier.TIER_3), PlayerTier.fromToken("3"));
        assertEquals(Optional.of(PlayerTier.TIER_1), PlayerTier.fromToken("t1"));
        assertEquals(Optional.of(PlayerTier.TIER_4), PlayerTier.fromToken("Tier 4"));
        assertTrue(PlayerTier.fromToken("9").isEmpty());
    }

    private static TeamService service(TestDatabaseSupport.Fixture fixture) {
        return new TeamService(
                new JdbcTeamRepository(fixture.dataSource()),
                fixture.accounts(),
                message -> {
                }
        );
    }
}
