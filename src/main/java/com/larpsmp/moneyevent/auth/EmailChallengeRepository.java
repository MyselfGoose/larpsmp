package com.larpsmp.moneyevent.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;

/**
 * JDBC persistence for one-time email verification challenges.
 */
public final class EmailChallengeRepository {

    private final DataSource dataSource;

    public EmailChallengeRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void invalidateActive(UUID accountId, EmailChallengePurpose purpose) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                UPDATE auth_email_challenges
                SET consumed_at = ?
                WHERE account_id = ?
                  AND purpose = ?
                  AND consumed_at IS NULL
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setObject(2, accountId);
            statement.setString(3, purpose.name());
            statement.executeUpdate();
        }
    }

    public EmailChallenge create(
            UUID accountId,
            EmailChallengePurpose purpose,
            String codeHash,
            Instant expiresAt,
            int maxAttempts
    ) throws SQLException {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();
        String sql = """
                INSERT INTO auth_email_challenges (
                    id, account_id, purpose, code_hash, expires_at,
                    attempt_count, max_attempts, consumed_at, created_at, last_sent_at
                )
                VALUES (?, ?, ?, ?, ?, 0, ?, NULL, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, accountId);
            statement.setString(3, purpose.name());
            statement.setString(4, codeHash);
            statement.setTimestamp(5, Timestamp.from(expiresAt));
            statement.setInt(6, maxAttempts);
            statement.setTimestamp(7, Timestamp.from(now));
            statement.setTimestamp(8, Timestamp.from(now));
            statement.executeUpdate();
        }
        return new EmailChallenge(
                id,
                accountId,
                purpose,
                codeHash,
                expiresAt,
                0,
                maxAttempts,
                null,
                now,
                now
        );
    }

    public Optional<EmailChallenge> findLatestActive(UUID accountId, EmailChallengePurpose purpose)
            throws SQLException {
        String sql = """
                SELECT id, account_id, purpose, code_hash, expires_at, attempt_count,
                       max_attempts, consumed_at, created_at, last_sent_at
                FROM auth_email_challenges
                WHERE account_id = ?
                  AND purpose = ?
                  AND consumed_at IS NULL
                ORDER BY created_at DESC
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.setString(2, purpose.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapChallenge(resultSet));
            }
        }
    }

    public void incrementAttempts(UUID challengeId) throws SQLException {
        String sql = """
                UPDATE auth_email_challenges
                SET attempt_count = attempt_count + 1
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, challengeId);
            statement.executeUpdate();
        }
    }

    public void markConsumed(UUID challengeId) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                UPDATE auth_email_challenges
                SET consumed_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setObject(2, challengeId);
            statement.executeUpdate();
        }
    }

    public void touchLastSent(UUID challengeId) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                UPDATE auth_email_challenges
                SET last_sent_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setObject(2, challengeId);
            statement.executeUpdate();
        }
    }

    private static EmailChallenge mapChallenge(ResultSet resultSet) throws SQLException {
        Timestamp consumed = resultSet.getTimestamp("consumed_at");
        return new EmailChallenge(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("account_id", UUID.class),
                EmailChallengePurpose.valueOf(resultSet.getString("purpose")),
                resultSet.getString("code_hash"),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getInt("attempt_count"),
                resultSet.getInt("max_attempts"),
                optionalInstant(consumed),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("last_sent_at").toInstant()
        );
    }

    private static @Nullable Instant optionalInstant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
