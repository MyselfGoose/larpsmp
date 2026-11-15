package com.larpsmp.moneyevent.team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;

/**
 * PostgreSQL persistence for teams and roster memberships.
 */
public final class JdbcTeamRepository {

    private static final String TEAM_COLUMNS = """
            id, slug, display_name, color, sort_order, captain_account_id, created_at, updated_at
            """;

    private final DataSource dataSource;

    public JdbcTeamRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public List<Team> listTeams() throws SQLException {
        String sql = "SELECT " + TEAM_COLUMNS + " FROM teams ORDER BY sort_order, display_name";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Team> teams = new ArrayList<>();
            while (resultSet.next()) {
                teams.add(mapTeam(resultSet));
            }
            return List.copyOf(teams);
        }
    }

    public Optional<Team> findById(UUID teamId) throws SQLException {
        String sql = "SELECT " + TEAM_COLUMNS + " FROM teams WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, teamId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTeam(resultSet));
            }
        }
    }

    public Optional<Team> findBySlug(String slug) throws SQLException {
        String sql = "SELECT " + TEAM_COLUMNS + " FROM teams WHERE slug = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, slug);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTeam(resultSet));
            }
        }
    }

    public Optional<Team> findByDisplayNameIgnoreCase(String displayName) throws SQLException {
        String sql = "SELECT " + TEAM_COLUMNS + " FROM teams WHERE LOWER(display_name) = LOWER(?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, displayName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTeam(resultSet));
            }
        }
    }

    public boolean slugExists(String slug, @Nullable UUID excludingTeamId) throws SQLException {
        String sql = excludingTeamId == null
                ? "SELECT 1 FROM teams WHERE slug = ?"
                : "SELECT 1 FROM teams WHERE slug = ? AND id <> ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, slug);
            if (excludingTeamId != null) {
                statement.setObject(2, excludingTeamId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public int nextSortOrder(Connection connection) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM teams";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    public void insertTeam(Connection connection, Team team) throws SQLException {
        String sql = """
                INSERT INTO teams (id, slug, display_name, color, sort_order, captain_account_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindTeam(statement, team);
            statement.executeUpdate();
        }
    }

    public void updateTeam(Connection connection, Team team) throws SQLException {
        String sql = """
                UPDATE teams
                SET slug = ?, display_name = ?, color = ?, sort_order = ?, captain_account_id = ?, updated_at = ?
                WHERE id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, team.slug());
            statement.setString(2, team.displayName());
            statement.setString(3, team.color().key());
            statement.setInt(4, team.sortOrder());
            if (team.captainAccountId() == null) {
                statement.setNull(5, Types.OTHER);
            } else {
                statement.setObject(5, team.captainAccountId());
            }
            statement.setTimestamp(6, Timestamp.from(team.updatedAt()));
            statement.setObject(7, team.id());
            statement.executeUpdate();
        }
    }

    public void deleteTeam(Connection connection, UUID teamId) throws SQLException {
        String sql = "DELETE FROM teams WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, teamId);
            statement.executeUpdate();
        }
    }

    public List<TeamMember> membersOf(UUID teamId) throws SQLException {
        String sql = """
                SELECT m.team_id, m.account_id, a.username, m.sort_order, m.joined_at, t.tier
                FROM team_memberships m
                INNER JOIN accounts a ON a.id = m.account_id
                LEFT JOIN competitor_tiers t ON t.account_id = m.account_id
                WHERE m.team_id = ?
                ORDER BY m.sort_order, a.username
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, teamId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TeamMember> members = new ArrayList<>();
                while (resultSet.next()) {
                    members.add(mapMember(resultSet));
                }
                return List.copyOf(members);
            }
        }
    }

    public Optional<TeamMember> findMembership(UUID accountId) throws SQLException {
        String sql = """
                SELECT m.team_id, m.account_id, a.username, m.sort_order, m.joined_at, t.tier
                FROM team_memberships m
                INNER JOIN accounts a ON a.id = m.account_id
                LEFT JOIN competitor_tiers t ON t.account_id = m.account_id
                WHERE m.account_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapMember(resultSet));
            }
        }
    }

    public Optional<PlayerTier> findTier(UUID accountId) throws SQLException {
        String sql = "SELECT tier FROM competitor_tiers WHERE account_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return PlayerTier.fromValue(resultSet.getInt("tier"));
            }
        }
    }

    public void upsertTier(UUID accountId, PlayerTier tier) throws SQLException {
        String sql = """
                INSERT INTO competitor_tiers (account_id, tier, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE
                SET tier = EXCLUDED.tier, updated_at = EXCLUDED.updated_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.setInt(2, tier.value());
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    public boolean deleteTier(UUID accountId) throws SQLException {
        String sql = "DELETE FROM competitor_tiers WHERE account_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            return statement.executeUpdate() > 0;
        }
    }

    /**
     * All rostered competitors across teams, with optional tier, for the admin tiers board.
     */
    public List<RosteredCompetitor> listRosteredCompetitors() throws SQLException {
        String sql = """
                SELECT m.account_id, a.username, m.team_id, tm.display_name, tm.color, t.tier
                FROM team_memberships m
                INNER JOIN accounts a ON a.id = m.account_id
                INNER JOIN teams tm ON tm.id = m.team_id
                LEFT JOIN competitor_tiers t ON t.account_id = m.account_id
                ORDER BY tm.sort_order, tm.display_name, a.username
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<RosteredCompetitor> rows = new ArrayList<>();
            while (resultSet.next()) {
                int tierValue = resultSet.getInt("tier");
                Integer tier = resultSet.wasNull() ? null : tierValue;
                rows.add(new RosteredCompetitor(
                        (UUID) resultSet.getObject("account_id"),
                        resultSet.getString("username"),
                        (UUID) resultSet.getObject("team_id"),
                        resultSet.getString("display_name"),
                        TeamColor.requireKey(resultSet.getString("color")),
                        tier
                ));
            }
            return List.copyOf(rows);
        }
    }

    public int memberCount(UUID teamId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM team_memberships WHERE team_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, teamId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public int nextMemberSortOrder(Connection connection, UUID teamId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM team_memberships WHERE team_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, teamId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public void insertMembership(Connection connection, UUID teamId, UUID accountId, int sortOrder, Instant joinedAt)
            throws SQLException {
        String sql = """
                INSERT INTO team_memberships (team_id, account_id, sort_order, joined_at)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, teamId);
            statement.setObject(2, accountId);
            statement.setInt(3, sortOrder);
            statement.setTimestamp(4, Timestamp.from(joinedAt));
            statement.executeUpdate();
        }
    }

    public void deleteMembership(Connection connection, UUID accountId) throws SQLException {
        String sql = "DELETE FROM team_memberships WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.executeUpdate();
        }
    }

    public void clearCaptainIf(Connection connection, UUID teamId, UUID accountId) throws SQLException {
        String sql = """
                UPDATE teams
                SET captain_account_id = NULL, updated_at = ?
                WHERE id = ? AND captain_account_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setObject(2, teamId);
            statement.setObject(3, accountId);
            statement.executeUpdate();
        }
    }

    public void setCaptain(Connection connection, UUID teamId, @Nullable UUID captainAccountId) throws SQLException {
        String sql = "UPDATE teams SET captain_account_id = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (captainAccountId == null) {
                statement.setNull(1, Types.OTHER);
            } else {
                statement.setObject(1, captainAccountId);
            }
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setObject(3, teamId);
            statement.executeUpdate();
        }
    }

    private static void bindTeam(PreparedStatement statement, Team team) throws SQLException {
        statement.setObject(1, team.id());
        statement.setString(2, team.slug());
        statement.setString(3, team.displayName());
        statement.setString(4, team.color().key());
        statement.setInt(5, team.sortOrder());
        if (team.captainAccountId() == null) {
            statement.setNull(6, Types.OTHER);
        } else {
            statement.setObject(6, team.captainAccountId());
        }
        statement.setTimestamp(7, Timestamp.from(team.createdAt()));
        statement.setTimestamp(8, Timestamp.from(team.updatedAt()));
    }

    private static Team mapTeam(ResultSet resultSet) throws SQLException {
        UUID captain = (UUID) resultSet.getObject("captain_account_id");
        return new Team(
                (UUID) resultSet.getObject("id"),
                resultSet.getString("slug"),
                resultSet.getString("display_name"),
                TeamColor.requireKey(resultSet.getString("color")),
                resultSet.getInt("sort_order"),
                captain,
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static TeamMember mapMember(ResultSet resultSet) throws SQLException {
        int tierValue = resultSet.getInt("tier");
        Integer tier = resultSet.wasNull() ? null : tierValue;
        return new TeamMember(
                (UUID) resultSet.getObject("team_id"),
                (UUID) resultSet.getObject("account_id"),
                resultSet.getString("username"),
                resultSet.getInt("sort_order"),
                resultSet.getTimestamp("joined_at").toInstant(),
                tier
        );
    }

    /**
     * Rostered account with team + optional tier for admin overviews.
     */
    public record RosteredCompetitor(
            UUID accountId,
            String username,
            UUID teamId,
            String teamDisplayName,
            TeamColor teamColor,
            @Nullable Integer tier
    ) {
        public Optional<PlayerTier> playerTier() {
            if (tier == null) {
                return Optional.empty();
            }
            return PlayerTier.fromValue(tier);
        }
    }
}
