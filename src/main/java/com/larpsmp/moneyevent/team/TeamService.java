package com.larpsmp.moneyevent.team;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.auth.AccountRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Authoritative team roster and definition mutations.
 *
 * <p>Competition soft rules (3 teams × 4 members, captains present) warn rather than block
 * during setup. Hard failures cover missing entities and invalid captain membership.
 */
public final class TeamService {

    public static final int EXPECTED_TEAM_COUNT = 3;
    public static final int EXPECTED_ROSTER_SIZE = 4;
    public static final int MAX_DISPLAY_NAME_LENGTH = 32;

    private static final Pattern SLUG_SANITIZE = Pattern.compile("[^a-z0-9]+");

    private final JdbcTeamRepository repository;
    private final AccountRepository accounts;
    private final Consumer<String> errorLogger;
    private final List<RosterChangeListener> rosterChangeListeners = new CopyOnWriteArrayList<>();

    public TeamService(
            JdbcTeamRepository repository,
            AccountRepository accounts,
            Consumer<String> errorLogger
    ) {
        this.repository = repository;
        this.accounts = accounts;
        this.errorLogger = errorLogger;
    }

    public void addRosterChangeListener(RosterChangeListener listener) {
        rosterChangeListeners.add(listener);
    }

    public List<Team> listTeams() {
        try {
            return repository.listTeams();
        } catch (SQLException exception) {
            errorLogger.accept("Failed to list teams: " + exception.getMessage());
            return List.of();
        }
    }

    public Optional<Team> getTeam(UUID teamId) {
        try {
            return repository.findById(teamId);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to load team " + teamId + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Team> findTeamByNameOrSlug(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String trimmed = token.trim();
        try {
            Optional<Team> bySlug = repository.findBySlug(slugify(trimmed));
            if (bySlug.isPresent()) {
                return bySlug;
            }
            return repository.findByDisplayNameIgnoreCase(trimmed);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to resolve team '" + trimmed + "': " + exception.getMessage());
            return Optional.empty();
        }
    }

    public List<TeamMember> membersOf(UUID teamId) {
        try {
            return repository.membersOf(teamId);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to list members for team " + teamId + ": " + exception.getMessage());
            return List.of();
        }
    }

    public Optional<Team> teamOf(UUID accountId) {
        try {
            Optional<TeamMember> membership = repository.findMembership(accountId);
            if (membership.isEmpty()) {
                return Optional.empty();
            }
            return repository.findById(membership.get().teamId());
        } catch (SQLException exception) {
            errorLogger.accept("Failed to resolve team for account " + accountId + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<TeamMember> membershipOf(UUID accountId) {
        try {
            return repository.findMembership(accountId);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to resolve membership for " + accountId + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    public List<String> rosterWarnings() {
        List<String> warnings = new ArrayList<>();
        try {
            List<Team> teams = repository.listTeams();
            if (teams.size() > EXPECTED_TEAM_COUNT) {
                warnings.add("There are " + teams.size() + " teams; competition expects "
                        + EXPECTED_TEAM_COUNT + ".");
            } else if (teams.size() < EXPECTED_TEAM_COUNT) {
                warnings.add("There are only " + teams.size() + " teams; competition expects "
                        + EXPECTED_TEAM_COUNT + ".");
            }
            for (Team team : teams) {
                warnings.addAll(warningsForTeam(team));
            }
            return List.copyOf(warnings);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to compute roster warnings: " + exception.getMessage());
            return List.of("Could not load roster warnings.");
        }
    }

    public TeamMutationResult createTeam(String displayName, TeamColor color) {
        String validatedName = validateDisplayName(displayName);
        if (validatedName == null) {
            return TeamMutationResult.fail("Team name must be 1–" + MAX_DISPLAY_NAME_LENGTH + " characters.");
        }
        if (color == null) {
            return TeamMutationResult.fail("A valid team color is required.");
        }
        try (Connection connection = repository.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                String slug = allocateSlug(connection, validatedName, null);
                Instant now = Instant.now();
                Team team = new Team(
                        UUID.randomUUID(),
                        slug,
                        validatedName,
                        color,
                        repository.nextSortOrder(connection),
                        null,
                        now,
                        now
                );
                repository.insertTeam(connection, team);
                connection.commit();
                return TeamMutationResult.ok(
                        "Created team " + team.displayName() + ".",
                        team,
                        rosterWarnings()
                );
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            errorLogger.accept("Failed to create team: " + exception.getMessage());
            return TeamMutationResult.fail("Could not create team.");
        }
    }

    public TeamMutationResult updateTeam(UUID teamId, @Nullable String displayName, @Nullable TeamColor color) {
        try (Connection connection = repository.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Team> existing = repository.findById(teamId);
                if (existing.isEmpty()) {
                    connection.rollback();
                    return TeamMutationResult.fail("That team does not exist.");
                }
                Team current = existing.get();
                String name = current.displayName();
                if (displayName != null) {
                    String validated = validateDisplayName(displayName);
                    if (validated == null) {
                        connection.rollback();
                        return TeamMutationResult.fail(
                                "Team name must be 1–" + MAX_DISPLAY_NAME_LENGTH + " characters.");
                    }
                    name = validated;
                }
                TeamColor resolvedColor = color == null ? current.color() : color;
                String slug = name.equals(current.displayName())
                        ? current.slug()
                        : allocateSlug(connection, name, teamId);
                Team updated = new Team(
                        current.id(),
                        slug,
                        name,
                        resolvedColor,
                        current.sortOrder(),
                        current.captainAccountId(),
                        current.createdAt(),
                        Instant.now()
                );
                repository.updateTeam(connection, updated);
                connection.commit();
                return TeamMutationResult.ok(
                        "Updated team " + updated.displayName() + ".",
                        updated,
                        warningsForTeam(updated)
                );
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            errorLogger.accept("Failed to update team " + teamId + ": " + exception.getMessage());
            return TeamMutationResult.fail("Could not update team.");
        }
    }

    public TeamMutationResult deleteTeam(UUID teamId) {
        try (Connection connection = repository.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Team> existing = repository.findById(teamId);
                if (existing.isEmpty()) {
                    connection.rollback();
                    return TeamMutationResult.fail("That team does not exist.");
                }
                String name = existing.get().displayName();
                repository.deleteTeam(connection, teamId);
                connection.commit();
                notifyRosterChanged(Set.of(teamId));
                return TeamMutationResult.ok("Deleted team " + name + ".", null, rosterWarnings());
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            errorLogger.accept("Failed to delete team " + teamId + ": " + exception.getMessage());
            return TeamMutationResult.fail("Could not delete team.");
        }
    }

    public TeamMutationResult assignMember(UUID accountId, UUID teamId) {
        try (Connection connection = repository.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Account> account = accounts.findById(accountId);
                if (account.isEmpty()) {
                    connection.rollback();
                    return TeamMutationResult.fail("That account does not exist.");
                }
                Optional<Team> team = repository.findById(teamId);
                if (team.isEmpty()) {
                    connection.rollback();
                    return TeamMutationResult.fail("That team does not exist.");
                }
                Optional<TeamMember> existing = repository.findMembership(accountId);
                String username = account.get().username();
                Set<UUID> affected = new HashSet<>();
                affected.add(teamId);
                if (existing.isPresent()) {
                    if (existing.get().teamId().equals(teamId)) {
                        connection.rollback();
                        return TeamMutationResult.fail(username + " is already on " + team.get().displayName() + ".");
                    }
                    affected.add(existing.get().teamId());
                    repository.clearCaptainIf(connection, existing.get().teamId(), accountId);
                    repository.deleteMembership(connection, accountId);
                }
                int sortOrder = repository.nextMemberSortOrder(connection, teamId);
                repository.insertMembership(connection, teamId, accountId, sortOrder, Instant.now());
                connection.commit();
                Team refreshed = repository.findById(teamId).orElse(team.get());
                notifyRosterChanged(affected);
                String message = existing.isPresent()
                        ? "Moved " + username + " to " + refreshed.displayName() + "."
                        : "Assigned " + username + " to " + refreshed.displayName() + ".";
                return TeamMutationResult.ok(message, refreshed, warningsForTeam(refreshed));
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            errorLogger.accept("Failed to assign member " + accountId + ": " + exception.getMessage());
            return TeamMutationResult.fail("Could not assign player to team.");
        }
    }

    public TeamMutationResult removeMember(UUID accountId) {
        try (Connection connection = repository.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<TeamMember> membership = repository.findMembership(accountId);
                if (membership.isEmpty()) {
                    connection.rollback();
                    return TeamMutationResult.fail("That player is not on a team.");
                }
                TeamMember member = membership.get();
                Optional<Team> team = repository.findById(member.teamId());
                repository.clearCaptainIf(connection, member.teamId(), accountId);
                repository.deleteMembership(connection, accountId);
                connection.commit();
                notifyRosterChanged(Set.of(member.teamId()));
                Team refreshed = team.flatMap(value -> {
                    try {
                        return repository.findById(value.id());
                    } catch (SQLException exception) {
                        return Optional.empty();
                    }
                }).orElse(null);
                List<String> warnings = refreshed == null ? rosterWarnings() : warningsForTeam(refreshed);
                return TeamMutationResult.ok(
                        "Removed " + member.username() + " from "
                                + (team.map(Team::displayName).orElse("their team")) + ".",
                        refreshed,
                        warnings
                );
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            errorLogger.accept("Failed to remove member " + accountId + ": " + exception.getMessage());
            return TeamMutationResult.fail("Could not remove player from team.");
        }
    }

    public Optional<PlayerTier> tierOf(UUID accountId) {
        try {
            return repository.findTier(accountId);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to load tier for " + accountId + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    public TeamMutationResult setTier(UUID accountId, PlayerTier tier) {
        if (tier == null) {
            return TeamMutationResult.fail("A valid tier (1–4) is required.");
        }
        try {
            Optional<Account> account = accounts.findById(accountId);
            if (account.isEmpty()) {
                return TeamMutationResult.fail("That account does not exist.");
            }
            repository.upsertTier(accountId, tier);
            Optional<TeamMember> membership = repository.findMembership(accountId);
            List<String> warnings = membership.isEmpty()
                    ? List.of()
                    : repository.findById(membership.get().teamId())
                            .map(team -> {
                                try {
                                    return warningsForTeam(team);
                                } catch (SQLException exception) {
                                    return List.<String>of();
                                }
                            })
                            .orElse(List.of());
            return TeamMutationResult.ok(
                    "Set " + account.get().username() + " to " + tier.displayName() + ".",
                    membership.flatMap(member -> {
                        try {
                            return repository.findById(member.teamId());
                        } catch (SQLException exception) {
                            return Optional.empty();
                        }
                    }).orElse(null),
                    warnings
            );
        } catch (SQLException exception) {
            errorLogger.accept("Failed to set tier for " + accountId + ": " + exception.getMessage());
            return TeamMutationResult.fail("Could not set tier.");
        }
    }

    public TeamMutationResult clearTier(UUID accountId) {
        try {
            Optional<Account> account = accounts.findById(accountId);
            if (account.isEmpty()) {
                return TeamMutationResult.fail("That account does not exist.");
            }
            boolean removed = repository.deleteTier(accountId);
            if (!removed) {
                return TeamMutationResult.fail(account.get().username() + " has no tier set.");
            }
            Optional<TeamMember> membership = repository.findMembership(accountId);
            List<String> warnings = membership.isEmpty()
                    ? List.of()
                    : repository.findById(membership.get().teamId())
                            .map(team -> {
                                try {
                                    return warningsForTeam(team);
                                } catch (SQLException exception) {
                                    return List.<String>of();
                                }
                            })
                            .orElse(List.of());
            return TeamMutationResult.ok(
                    "Cleared tier for " + account.get().username() + ".",
                    membership.flatMap(member -> {
                        try {
                            return repository.findById(member.teamId());
                        } catch (SQLException exception) {
                            return Optional.empty();
                        }
                    }).orElse(null),
                    warnings
            );
        } catch (SQLException exception) {
            errorLogger.accept("Failed to clear tier for " + accountId + ": " + exception.getMessage());
            return TeamMutationResult.fail("Could not clear tier.");
        }
    }

    public List<JdbcTeamRepository.RosteredCompetitor> listRosteredCompetitors() {
        try {
            return repository.listRosteredCompetitors();
        } catch (SQLException exception) {
            errorLogger.accept("Failed to list rostered competitors: " + exception.getMessage());
            return List.of();
        }
    }

    public TeamMutationResult setCaptain(UUID teamId, UUID accountId) {
        try (Connection connection = repository.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Team> team = repository.findById(teamId);
                if (team.isEmpty()) {
                    connection.rollback();
                    return TeamMutationResult.fail("That team does not exist.");
                }
                Optional<Account> account = accounts.findById(accountId);
                if (account.isEmpty()) {
                    connection.rollback();
                    return TeamMutationResult.fail("That account does not exist.");
                }
                Optional<TeamMember> membership = repository.findMembership(accountId);
                if (membership.isEmpty() || !membership.get().teamId().equals(teamId)) {
                    connection.rollback();
                    return TeamMutationResult.fail(
                            account.get().username() + " must be a member of "
                                    + team.get().displayName() + " to become captain.");
                }
                repository.setCaptain(connection, teamId, accountId);
                connection.commit();
                Team refreshed = repository.findById(teamId).orElse(team.get());
                return TeamMutationResult.ok(
                        "Set " + account.get().username() + " as captain of " + refreshed.displayName() + ".",
                        refreshed,
                        warningsForTeam(refreshed)
                );
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            errorLogger.accept("Failed to set captain for " + teamId + ": " + exception.getMessage());
            return TeamMutationResult.fail("Could not set captain.");
        }
    }

    public TeamMutationResult clearCaptain(UUID teamId) {
        try (Connection connection = repository.dataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<Team> team = repository.findById(teamId);
                if (team.isEmpty()) {
                    connection.rollback();
                    return TeamMutationResult.fail("That team does not exist.");
                }
                if (team.get().captainAccountId() == null) {
                    connection.rollback();
                    return TeamMutationResult.fail(team.get().displayName() + " has no captain.");
                }
                repository.setCaptain(connection, teamId, null);
                connection.commit();
                Team refreshed = repository.findById(teamId).orElse(team.get());
                return TeamMutationResult.ok(
                        "Cleared captain for " + refreshed.displayName() + ".",
                        refreshed,
                        warningsForTeam(refreshed)
                );
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            errorLogger.accept("Failed to clear captain for " + teamId + ": " + exception.getMessage());
            return TeamMutationResult.fail("Could not clear captain.");
        }
    }

    public Optional<Account> findAccountByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        try {
            return accounts.findByUsername(username.trim());
        } catch (SQLException exception) {
            errorLogger.accept("Failed to look up account '" + username + "': " + exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Account> findAccountById(UUID accountId) {
        try {
            return accounts.findById(accountId);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to look up account " + accountId + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Registered accounts for assign pickers / command tab-complete, ordered by username.
     */
    public List<Account> listAccounts() {
        try {
            return accounts.listAccounts();
        } catch (SQLException exception) {
            errorLogger.accept("Failed to list accounts: " + exception.getMessage());
            return List.of();
        }
    }

    public List<String> listUsernames() {
        return listAccounts().stream().map(Account::username).toList();
    }

    public Optional<String> captainUsername(Team team) {
        if (team.captainAccountId() == null) {
            return Optional.empty();
        }
        try {
            return accounts.findById(team.captainAccountId()).map(Account::username);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to resolve captain username: " + exception.getMessage());
            return Optional.empty();
        }
    }

    private List<String> warningsForTeam(Team team) throws SQLException {
        List<String> warnings = new ArrayList<>();
        List<TeamMember> members = repository.membersOf(team.id());
        int count = members.size();
        if (count == 0) {
            warnings.add(team.displayName() + " has no members.");
        } else if (count < EXPECTED_ROSTER_SIZE) {
            warnings.add(team.displayName() + " has " + count + "/" + EXPECTED_ROSTER_SIZE + " members.");
        } else if (count > EXPECTED_ROSTER_SIZE) {
            warnings.add(team.displayName() + " has " + count + " members; competition roster is "
                    + EXPECTED_ROSTER_SIZE + ".");
        }
        if (team.captainAccountId() == null) {
            warnings.add(team.displayName() + " has no captain.");
        } else {
            Optional<TeamMember> captainMembership = repository.findMembership(team.captainAccountId());
            if (captainMembership.isEmpty() || !captainMembership.get().teamId().equals(team.id())) {
                warnings.add(team.displayName() + " captain is not on the team roster.");
            }
        }
        Map<Integer, List<String>> byTier = new HashMap<>();
        for (TeamMember member : members) {
            if (member.tier() == null) {
                warnings.add(team.displayName() + " member " + member.username() + " has no tier.");
            } else {
                byTier.computeIfAbsent(member.tier(), ignored -> new ArrayList<>()).add(member.username());
            }
        }
        for (Map.Entry<Integer, List<String>> entry : byTier.entrySet()) {
            if (entry.getValue().size() > 1) {
                warnings.add(team.displayName() + " has " + entry.getValue().size()
                        + " Tier " + entry.getKey() + " players ("
                        + String.join(", ", entry.getValue()) + ").");
            }
        }
        return warnings;
    }

    private void notifyRosterChanged(Set<UUID> affectedTeamIds) {
        if (affectedTeamIds.isEmpty()) {
            return;
        }
        Set<UUID> immutable = Set.copyOf(affectedTeamIds);
        for (RosterChangeListener listener : rosterChangeListeners) {
            listener.rosterChanged(immutable);
        }
    }

    private String allocateSlug(Connection connection, String displayName, @Nullable UUID excludingTeamId)
            throws SQLException {
        String base = slugify(displayName);
        if (base.isBlank()) {
            base = "team";
        }
        String candidate = base;
        int suffix = 2;
        while (repository.slugExists(candidate, excludingTeamId)) {
            // Re-check through connection-scoped uniqueness by querying the same repository method;
            // slugExists opens its own connection which is fine under READ COMMITTED for allocation.
            candidate = base + "-" + suffix;
            suffix++;
            if (suffix > 1000) {
                throw new SQLException("Could not allocate unique team slug for " + displayName);
            }
        }
        // Double-check within the transaction connection directly
        String sql = excludingTeamId == null
                ? "SELECT 1 FROM teams WHERE slug = ?"
                : "SELECT 1 FROM teams WHERE slug = ? AND id <> ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, candidate);
            if (excludingTeamId != null) {
                statement.setObject(2, excludingTeamId);
            }
            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    // Extremely unlikely race; fall back to UUID suffix.
                    candidate = base + "-" + UUID.randomUUID().toString().substring(0, 8);
                }
            }
        }
        return candidate;
    }

    static String slugify(String displayName) {
        String normalized = displayName.trim().toLowerCase(Locale.ROOT);
        normalized = SLUG_SANITIZE.matcher(normalized).replaceAll("-");
        while (normalized.startsWith("-")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("-")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.length() > 48) {
            normalized = normalized.substring(0, 48);
            while (normalized.endsWith("-")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
        }
        return normalized;
    }

    private static @Nullable String validateDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String trimmed = displayName.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty() || trimmed.length() > MAX_DISPLAY_NAME_LENGTH) {
            return null;
        }
        return trimmed;
    }
}
