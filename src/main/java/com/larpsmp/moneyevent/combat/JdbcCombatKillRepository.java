package com.larpsmp.moneyevent.combat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * PostgreSQL persistence for attributed combat kills.
 */
public final class JdbcCombatKillRepository {

    private final DataSource dataSource;

    public JdbcCombatKillRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void insert(CombatKillRecord record) throws SQLException {
        String sql = """
                INSERT INTO combat_kills (
                    death_id, victim_account_id, killer_account_id, cause, evidence,
                    event_kill, friendly_fire, settlement_outcome, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (death_id) DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.deathId());
            statement.setObject(2, record.victimAccountId());
            statement.setObject(3, record.killerAccountId());
            statement.setString(4, record.cause().name());
            statement.setString(5, record.evidence());
            statement.setBoolean(6, record.eventKill());
            statement.setBoolean(7, record.friendlyFire());
            statement.setString(8, record.settlementOutcome());
            statement.setTimestamp(9, Timestamp.from(record.createdAt()));
            statement.executeUpdate();
        }
    }

    public Optional<CombatKillRecord> findByDeathId(String deathId) throws SQLException {
        String sql = """
                SELECT death_id, victim_account_id, killer_account_id, cause, evidence,
                       event_kill, friendly_fire, settlement_outcome, created_at
                FROM combat_kills
                WHERE death_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deathId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    /**
     * Normal (non-event, non-friendly-fire) kill counts by killer for future {@code /top kills}.
     */
    public List<KillerKillCount> normalKillLeaderboard(int limit) throws SQLException {
        if (limit < 1) {
            return List.of();
        }
        String sql = """
                SELECT k.killer_account_id, COUNT(*) AS kill_count
                FROM combat_kills k
                INNER JOIN account_participation killer
                    ON killer.account_id = k.killer_account_id AND killer.competitor = TRUE
                INNER JOIN account_participation victim
                    ON victim.account_id = k.victim_account_id AND victim.competitor = TRUE
                WHERE k.event_kill = FALSE AND k.friendly_fire = FALSE
                GROUP BY k.killer_account_id
                ORDER BY kill_count DESC, k.killer_account_id
                LIMIT ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<KillerKillCount> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new KillerKillCount(
                            resultSet.getObject("killer_account_id", UUID.class),
                            resultSet.getLong("kill_count")));
                }
                return List.copyOf(rows);
            }
        }
    }

    private static CombatKillRecord mapRow(ResultSet resultSet) throws SQLException {
        Timestamp created = resultSet.getTimestamp("created_at");
        Instant createdAt = created == null ? Instant.EPOCH : created.toInstant();
        return new CombatKillRecord(
                resultSet.getString("death_id"),
                resultSet.getObject("victim_account_id", UUID.class),
                resultSet.getObject("killer_account_id", UUID.class),
                AttributionCause.valueOf(resultSet.getString("cause")),
                resultSet.getString("evidence"),
                resultSet.getBoolean("event_kill"),
                resultSet.getBoolean("friendly_fire"),
                resultSet.getString("settlement_outcome"),
                createdAt
        );
    }

    public record KillerKillCount(UUID killerAccountId, long kills) {
    }
}
