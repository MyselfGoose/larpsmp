package com.larpsmp.moneyevent.team;

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
 * Persistence for competitor / spectator participation flags.
 */
public final class AccountParticipationRepository {

    private final DataSource dataSource;

    public AccountParticipationRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<AccountParticipation> find(UUID accountId) throws SQLException {
        String sql = """
                SELECT account_id, competitor, spectator, updated_at
                FROM account_participation
                WHERE account_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    public Optional<AccountParticipation> find(Connection connection, UUID accountId) throws SQLException {
        String sql = """
                SELECT account_id, competitor, spectator, updated_at
                FROM account_participation
                WHERE account_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRow(resultSet));
            }
        }
    }

    public boolean isCompetitor(UUID accountId) throws SQLException {
        return find(accountId).map(AccountParticipation::competitor).orElse(false);
    }

    public boolean isSpectator(UUID accountId) throws SQLException {
        return find(accountId).map(AccountParticipation::spectator).orElse(false);
    }

    public List<UUID> listCompetitorAccountIds() throws SQLException {
        String sql = """
                SELECT account_id
                FROM account_participation
                WHERE competitor = TRUE
                ORDER BY account_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<UUID> ids = new ArrayList<>();
            while (resultSet.next()) {
                ids.add(resultSet.getObject("account_id", UUID.class));
            }
            return List.copyOf(ids);
        }
    }

    /**
     * Upserts participation. When both flags are false the row is deleted.
     */
    public void upsert(UUID accountId, boolean competitor, boolean spectator) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            upsert(connection, accountId, competitor, spectator);
        }
    }

    public void upsert(Connection connection, UUID accountId, boolean competitor, boolean spectator)
            throws SQLException {
        if (competitor && spectator) {
            throw new IllegalArgumentException("Account cannot be both competitor and spectator");
        }
        if (!competitor && !spectator) {
            delete(connection, accountId);
            return;
        }
        String sql = """
                INSERT INTO account_participation (account_id, competitor, spectator, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE
                SET competitor = EXCLUDED.competitor,
                    spectator = EXCLUDED.spectator,
                    updated_at = EXCLUDED.updated_at
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.setBoolean(2, competitor);
            statement.setBoolean(3, spectator);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    public void clearCompetitor(Connection connection, UUID accountId) throws SQLException {
        Optional<AccountParticipation> existing = find(connection, accountId);
        if (existing.isEmpty()) {
            return;
        }
        AccountParticipation row = existing.get();
        if (row.spectator()) {
            upsert(connection, accountId, false, true);
        } else {
            delete(connection, accountId);
        }
    }

    public void delete(Connection connection, UUID accountId) throws SQLException {
        String sql = "DELETE FROM account_participation WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.executeUpdate();
        }
    }

    private static AccountParticipation mapRow(ResultSet resultSet) throws SQLException {
        Timestamp updated = resultSet.getTimestamp("updated_at");
        return new AccountParticipation(
                resultSet.getObject("account_id", UUID.class),
                resultSet.getBoolean("competitor"),
                resultSet.getBoolean("spectator"),
                updated == null ? Instant.EPOCH : updated.toInstant());
    }
}
