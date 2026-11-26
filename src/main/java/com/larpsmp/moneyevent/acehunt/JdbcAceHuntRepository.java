package com.larpsmp.moneyevent.acehunt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;

/**
 * PostgreSQL persistence for Ace Hunt sessions and related rows.
 */
public final class JdbcAceHuntRepository {

    private static final String RUNTIME_KEY = "default";

    private final DataSource dataSource;

    public JdbcAceHuntRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsertSession(AceHuntSession session) throws SQLException {
        String sql = """
                INSERT INTO ace_hunt_sessions (
                    session_id, phase, hunt_started_at, surface_started_at, ban_started_at,
                    ended_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id) DO UPDATE SET
                    phase = EXCLUDED.phase,
                    hunt_started_at = EXCLUDED.hunt_started_at,
                    surface_started_at = EXCLUDED.surface_started_at,
                    ban_started_at = EXCLUDED.ban_started_at,
                    ended_at = EXCLUDED.ended_at,
                    updated_at = EXCLUDED.updated_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, session.sessionId());
            statement.setString(2, session.phase().name());
            setInstant(statement, 3, session.huntStartedAt());
            setInstant(statement, 4, session.surfaceStartedAt());
            setInstant(statement, 5, session.banStartedAt());
            setInstant(statement, 6, session.endedAt());
            statement.setTimestamp(7, Timestamp.from(session.createdAt()));
            statement.setTimestamp(8, Timestamp.from(session.updatedAt()));
            statement.executeUpdate();
        }
    }

    public Optional<AceHuntSession> findSession(UUID sessionId) throws SQLException {
        String sql = """
                SELECT session_id, phase, hunt_started_at, surface_started_at, ban_started_at,
                       ended_at, created_at, updated_at
                FROM ace_hunt_sessions
                WHERE session_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                AceHuntSession session = mapSession(resultSet);
                hydrate(session);
                return Optional.of(session);
            }
        }
    }

    public void replaceAces(UUID sessionId, Map<UUID, UUID> acesByTeam) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM ace_hunt_aces WHERE session_id = ?")) {
                    delete.setObject(1, sessionId);
                    delete.executeUpdate();
                }
                String insert = """
                        INSERT INTO ace_hunt_aces (session_id, team_id, account_id, alive, death_order, spectator)
                        VALUES (?, ?, ?, TRUE, NULL, FALSE)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    for (Map.Entry<UUID, UUID> entry : acesByTeam.entrySet()) {
                        statement.setObject(1, sessionId);
                        statement.setObject(2, entry.getKey());
                        statement.setObject(3, entry.getValue());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public void replaceAssignments(UUID sessionId, Map<UUID, UUID> hunterToTarget) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM ace_hunt_assignments WHERE session_id = ?")) {
                    delete.setObject(1, sessionId);
                    delete.executeUpdate();
                }
                String insert = """
                        INSERT INTO ace_hunt_assignments (session_id, hunter_team_id, target_team_id)
                        VALUES (?, ?, ?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    for (Map.Entry<UUID, UUID> entry : hunterToTarget.entrySet()) {
                        statement.setObject(1, sessionId);
                        statement.setObject(2, entry.getKey());
                        statement.setObject(3, entry.getValue());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public boolean insertScoredDeath(UUID sessionId, AceHuntScoredDeath death) throws SQLException {
        String sql = """
                INSERT INTO ace_hunt_scored_deaths (
                    session_id, ace_team_id, ace_account_id, death_id, killer_account_id,
                    killer_team_id, hunt_scored, death_order, died_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id, ace_team_id) DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, death.aceTeamId());
            statement.setObject(3, death.aceAccountId());
            statement.setString(4, death.deathId());
            setUuid(statement, 5, death.killerAccountId());
            setUuid(statement, 6, death.killerTeamId());
            statement.setBoolean(7, death.huntScored());
            statement.setInt(8, death.deathOrder());
            statement.setTimestamp(9, Timestamp.from(death.diedAt()));
            return statement.executeUpdate() > 0;
        }
    }

    public void markAceDead(UUID sessionId, UUID teamId, int deathOrder) throws SQLException {
        String sql = """
                UPDATE ace_hunt_aces
                SET alive = FALSE, death_order = ?, spectator = TRUE
                WHERE session_id = ? AND team_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, deathOrder);
            statement.setObject(2, sessionId);
            statement.setObject(3, teamId);
            statement.executeUpdate();
        }
    }

    public void upsertLockout(UUID sessionId, AceHuntLockout lockout) throws SQLException {
        String sql = """
                INSERT INTO ace_hunt_lockouts (session_id, account_id, release_at, hunter_spawn_team_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (session_id, account_id) DO UPDATE SET
                    release_at = EXCLUDED.release_at,
                    hunter_spawn_team_id = EXCLUDED.hunter_spawn_team_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, lockout.accountId());
            statement.setTimestamp(3, Timestamp.from(lockout.releaseAt()));
            statement.setObject(4, lockout.hunterSpawnTeamId());
            statement.executeUpdate();
        }
    }

    public void deleteLockout(UUID sessionId, UUID accountId) throws SQLException {
        String sql = "DELETE FROM ace_hunt_lockouts WHERE session_id = ? AND account_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, accountId);
            statement.executeUpdate();
        }
    }

    public void upsertSnapshot(UUID sessionId, AceHuntLocationSnapshot snapshot) throws SQLException {
        String sql = """
                INSERT INTO ace_hunt_snapshots (
                    session_id, account_id, world_name, x, y, z, yaw, pitch, role
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id, account_id) DO UPDATE SET
                    world_name = EXCLUDED.world_name,
                    x = EXCLUDED.x,
                    y = EXCLUDED.y,
                    z = EXCLUDED.z,
                    yaw = EXCLUDED.yaw,
                    pitch = EXCLUDED.pitch,
                    role = EXCLUDED.role
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, snapshot.accountId());
            statement.setString(3, snapshot.worldName());
            statement.setDouble(4, snapshot.x());
            statement.setDouble(5, snapshot.y());
            statement.setDouble(6, snapshot.z());
            statement.setFloat(7, snapshot.yaw());
            statement.setFloat(8, snapshot.pitch());
            statement.setString(9, snapshot.role().name());
            statement.executeUpdate();
        }
    }

    public void upsertSpectator(UUID sessionId, UUID accountId, String reason) throws SQLException {
        String sql = """
                INSERT INTO ace_hunt_spectators (session_id, account_id, reason)
                VALUES (?, ?, ?)
                ON CONFLICT (session_id, account_id) DO UPDATE SET reason = EXCLUDED.reason
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, accountId);
            statement.setString(3, reason == null ? "" : reason);
            statement.executeUpdate();
        }
    }

    public void setRuntime(@Nullable UUID activeSessionId, Instant now) throws SQLException {
        String sql = """
                UPDATE ace_hunt_runtime
                SET active_session_id = ?, updated_at = ?
                WHERE singleton_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setUuid(statement, 1, activeSessionId);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, RUNTIME_KEY);
            statement.executeUpdate();
        }
    }

    public Optional<UUID> loadActiveSessionId() throws SQLException {
        String sql = "SELECT active_session_id FROM ace_hunt_runtime WHERE singleton_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, RUNTIME_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                Object value = resultSet.getObject("active_session_id");
                return value == null ? Optional.empty() : Optional.of((UUID) value);
            }
        }
    }

    private void hydrate(AceHuntSession session) throws SQLException {
        session.loadAces(loadAces(session.sessionId()));
        session.loadAssignments(loadAssignments(session.sessionId()));
        session.loadScoredDeaths(loadScoredDeaths(session.sessionId()));
        session.loadLockouts(loadLockouts(session.sessionId()));
        session.loadSnapshots(loadSnapshots(session.sessionId()));
        session.loadSpectators(loadSpectators(session.sessionId()));
    }

    private Map<UUID, UUID> loadAces(UUID sessionId) throws SQLException {
        String sql = "SELECT team_id, account_id FROM ace_hunt_aces WHERE session_id = ?";
        Map<UUID, UUID> aces = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    aces.put((UUID) resultSet.getObject("team_id"), (UUID) resultSet.getObject("account_id"));
                }
            }
        }
        return aces;
    }

    private Map<UUID, UUID> loadAssignments(UUID sessionId) throws SQLException {
        String sql = """
                SELECT hunter_team_id, target_team_id
                FROM ace_hunt_assignments
                WHERE session_id = ?
                """;
        Map<UUID, UUID> assignments = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    assignments.put(
                            (UUID) resultSet.getObject("hunter_team_id"),
                            (UUID) resultSet.getObject("target_team_id"));
                }
            }
        }
        return assignments;
    }

    private List<AceHuntScoredDeath> loadScoredDeaths(UUID sessionId) throws SQLException {
        String sql = """
                SELECT ace_team_id, ace_account_id, death_id, killer_account_id, killer_team_id,
                       hunt_scored, death_order, died_at
                FROM ace_hunt_scored_deaths
                WHERE session_id = ?
                ORDER BY death_order ASC
                """;
        List<AceHuntScoredDeath> deaths = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    deaths.add(new AceHuntScoredDeath(
                            (UUID) resultSet.getObject("ace_team_id"),
                            (UUID) resultSet.getObject("ace_account_id"),
                            resultSet.getString("death_id"),
                            (UUID) resultSet.getObject("killer_account_id"),
                            (UUID) resultSet.getObject("killer_team_id"),
                            resultSet.getBoolean("hunt_scored"),
                            resultSet.getInt("death_order"),
                            resultSet.getTimestamp("died_at").toInstant()
                    ));
                }
            }
        }
        return deaths;
    }

    private Map<UUID, AceHuntLockout> loadLockouts(UUID sessionId) throws SQLException {
        String sql = """
                SELECT account_id, release_at, hunter_spawn_team_id
                FROM ace_hunt_lockouts
                WHERE session_id = ?
                """;
        Map<UUID, AceHuntLockout> lockouts = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID accountId = (UUID) resultSet.getObject("account_id");
                    lockouts.put(accountId, new AceHuntLockout(
                            accountId,
                            resultSet.getTimestamp("release_at").toInstant(),
                            (UUID) resultSet.getObject("hunter_spawn_team_id")
                    ));
                }
            }
        }
        return lockouts;
    }

    private Map<UUID, AceHuntLocationSnapshot> loadSnapshots(UUID sessionId) throws SQLException {
        String sql = """
                SELECT account_id, world_name, x, y, z, yaw, pitch, role
                FROM ace_hunt_snapshots
                WHERE session_id = ?
                """;
        Map<UUID, AceHuntLocationSnapshot> snapshots = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID accountId = (UUID) resultSet.getObject("account_id");
                    snapshots.put(accountId, new AceHuntLocationSnapshot(
                            accountId,
                            resultSet.getString("world_name"),
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            resultSet.getFloat("yaw"),
                            resultSet.getFloat("pitch"),
                            AceHuntLocationSnapshot.ParticipantRole.valueOf(resultSet.getString("role"))
                    ));
                }
            }
        }
        return snapshots;
    }

    private Map<UUID, String> loadSpectators(UUID sessionId) throws SQLException {
        String sql = "SELECT account_id, reason FROM ace_hunt_spectators WHERE session_id = ?";
        Map<UUID, String> spectators = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    spectators.put(
                            (UUID) resultSet.getObject("account_id"),
                            resultSet.getString("reason"));
                }
            }
        }
        return spectators;
    }

    private static AceHuntSession mapSession(ResultSet resultSet) throws SQLException {
        return new AceHuntSession(
                (UUID) resultSet.getObject("session_id"),
                AceHuntPhase.valueOf(resultSet.getString("phase")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                instantOrNull(resultSet, "hunt_started_at"),
                instantOrNull(resultSet, "surface_started_at"),
                instantOrNull(resultSet, "ban_started_at"),
                instantOrNull(resultSet, "ended_at")
        );
    }

    private static @Nullable Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static void setInstant(PreparedStatement statement, int index, @Nullable Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static void setUuid(PreparedStatement statement, int index, @Nullable UUID value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }
}
