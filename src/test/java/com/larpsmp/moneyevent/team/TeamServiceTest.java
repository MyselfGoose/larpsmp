package com.larpsmp.moneyevent.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamServiceTest {

    @Test
    void seedsDefaultTeamsAndSupportsFullCrud() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("TeamServiceTest-crud")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TeamService teams = service(fixture);

            List<Team> seeded = teams.listTeams();
            assertTrue(seeded.size() >= 3, "expected seeded Blue/Red/Yellow");
            assertTrue(seeded.stream().anyMatch(team -> team.slug().equals("blue")));
            assertTrue(seeded.stream().anyMatch(team -> team.slug().equals("red")));
            assertTrue(seeded.stream().anyMatch(team -> team.slug().equals("yellow")));

            TeamMutationResult created = teams.createTeam("Green", TeamColor.GREEN);
            assertTrue(created.success(), () -> String.valueOf(created.error()));
            assertEquals("Green", created.team().displayName());
            assertEquals(TeamColor.GREEN, created.team().color());

            TeamMutationResult renamed = teams.updateTeam(created.team().id(), "Emerald", TeamColor.AQUA);
            assertTrue(renamed.success());
            assertEquals("Emerald", renamed.team().displayName());
            assertEquals(TeamColor.AQUA, renamed.team().color());

            TeamMutationResult deleted = teams.deleteTeam(created.team().id());
            assertTrue(deleted.success());
            assertTrue(teams.getTeam(created.team().id()).isEmpty());
        }
    }

    @Test
    void assignMoveRemoveAndCaptainRules() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("TeamServiceTest-roster")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TeamService teams = service(fixture);
            TestDatabaseSupport.RegisteredPlayer alpha = fixture.register("Alpha");
            TestDatabaseSupport.RegisteredPlayer beta = fixture.register("Beta");
            Account alphaAccount = fixture.accounts().findByUsername(alpha.username()).orElseThrow();
            Account betaAccount = fixture.accounts().findByUsername(beta.username()).orElseThrow();

            Team blue = teams.findTeamByNameOrSlug("blue").orElseThrow();
            Team red = teams.findTeamByNameOrSlug("red").orElseThrow();

            TeamMutationResult assign = teams.assignMember(alphaAccount.id(), blue.id());
            assertTrue(assign.success());
            assertEquals(blue.id(), teams.teamOf(alphaAccount.id()).orElseThrow().id());

            TeamMutationResult captainFail = teams.setCaptain(blue.id(), betaAccount.id());
            assertFalse(captainFail.success());

            assertTrue(teams.assignMember(betaAccount.id(), blue.id()).success());
            TeamMutationResult captainOk = teams.setCaptain(blue.id(), betaAccount.id());
            assertTrue(captainOk.success());
            assertEquals(betaAccount.id(), teams.getTeam(blue.id()).orElseThrow().captainAccountId());

            TeamMutationResult moved = teams.assignMember(betaAccount.id(), red.id());
            assertTrue(moved.success());
            assertEquals(red.id(), teams.teamOf(betaAccount.id()).orElseThrow().id());
            assertEquals(null, teams.getTeam(blue.id()).orElseThrow().captainAccountId());

            TeamMutationResult removed = teams.removeMember(alphaAccount.id());
            assertTrue(removed.success());
            assertTrue(teams.teamOf(alphaAccount.id()).isEmpty());
        }
    }

    @Test
    void oneMembershipPerAccountAndSoftWarnings() throws Exception {
        try (TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("TeamServiceTest-warnings")) {
            assumeTrue(fixture != null, "Postgres unavailable");
            TeamService teams = service(fixture);
            Team blue = teams.findTeamByNameOrSlug("blue").orElseThrow();

            TeamMutationResult created = teams.createTeam("Extra", TeamColor.WHITE);
            assertTrue(created.success());
            List<String> warnings = teams.rosterWarnings();
            assertTrue(warnings.stream().anyMatch(text -> text.contains("teams")), warnings::toString);

            TestDatabaseSupport.RegisteredPlayer one = fixture.register("One");
            Account account = fixture.accounts().findByUsername(one.username()).orElseThrow();
            assertTrue(teams.assignMember(account.id(), blue.id()).success());
            assertFalse(teams.assignMember(account.id(), blue.id()).success());

            Optional<TeamMember> membership = teams.membershipOf(account.id());
            assertTrue(membership.isPresent());
            assertEquals(1, teams.membersOf(blue.id()).stream()
                    .filter(member -> member.accountId().equals(account.id()))
                    .count());

            List<String> blueWarnings = teams.rosterWarnings();
            assertTrue(blueWarnings.stream().anyMatch(text -> text.contains("Blue") && text.contains("members")),
                    blueWarnings::toString);
            assertTrue(blueWarnings.stream().anyMatch(text -> text.contains("no captain")),
                    blueWarnings::toString);

            // cleanup extra team so later tests are not poisoned by permanent Extra
            teams.deleteTeam(created.team().id());
            teams.removeMember(account.id());
        }
    }

    @Test
    void slugifyProducesStableKeys() {
        assertEquals("blue-team", TeamService.slugify("Blue Team"));
        assertEquals("emerald", TeamService.slugify("  Emerald  "));
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
