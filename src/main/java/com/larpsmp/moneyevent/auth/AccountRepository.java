package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.wallet.JdbcMoneyRepository;
import com.larpsmp.moneyevent.wallet.MoneyService;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;

/**
 * JDBC persistence for accounts and Minecraft identity bindings.
 */
public final class AccountRepository {

    private static final String ACCOUNT_COLUMNS = """
            id, username, email, password_hash, email_verified, email_verified_at,
            created_at, updated_at, last_login_at
            """;

    private final DataSource dataSource;

    public AccountRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<Account> findByUsername(String username) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM accounts WHERE username = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapAccount(resultSet));
            }
        }
    }

    public Optional<Account> findByEmail(String email) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM accounts WHERE email = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapAccount(resultSet));
            }
        }
    }

    public Optional<Account> findByUsernameOrEmail(String identifier) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM accounts WHERE username = ? OR email = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identifier);
            statement.setString(2, identifier);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapAccount(resultSet));
            }
        }
    }

    public Optional<Account> findById(UUID accountId) throws SQLException {
        String sql = "SELECT " + ACCOUNT_COLUMNS + " FROM accounts WHERE id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapAccount(resultSet));
            }
        }
    }

    public Optional<AccountIdentity> findIdentityByMinecraftUuid(UUID minecraftUuid) throws SQLException {
        String sql = """
                SELECT id, account_id, minecraft_uuid, minecraft_name, bound_at, last_seen_at
                FROM account_minecraft_identities
                WHERE minecraft_uuid = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapIdentity(resultSet));
            }
        }
    }

    public Optional<AccountIdentity> findIdentityByAccountId(UUID accountId) throws SQLException {
        String sql = """
                SELECT id, account_id, minecraft_uuid, minecraft_name, bound_at, last_seen_at
                FROM account_minecraft_identities
                WHERE account_id = ?
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapIdentity(resultSet));
            }
        }
    }

    /**
     * Creates an unverified account, binds the connecting Minecraft UUID, and provisions a
     * starting wallet ({@link MoneyService#STARTING_BALANCE}) in one transaction.
     */
    public Account createAccountWithIdentity(
            String username,
            String email,
            String passwordHash,
            UUID minecraftUuid,
            @Nullable String minecraftName
    ) throws SQLException {
        Instant now = Instant.now();
        UUID accountId = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();

        String insertAccount = """
                INSERT INTO accounts (
                    id, username, email, password_hash, email_verified, email_verified_at,
                    created_at, updated_at, last_login_at
                )
                VALUES (?, ?, ?, ?, FALSE, NULL, ?, ?, NULL)
                """;
        String insertIdentity = """
                INSERT INTO account_minecraft_identities
                    (id, account_id, minecraft_uuid, minecraft_name, bound_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement accountStatement = connection.prepareStatement(insertAccount)) {
                    accountStatement.setObject(1, accountId);
                    accountStatement.setString(2, username);
                    accountStatement.setString(3, email);
                    accountStatement.setString(4, passwordHash);
                    accountStatement.setTimestamp(5, Timestamp.from(now));
                    accountStatement.setTimestamp(6, Timestamp.from(now));
                    accountStatement.executeUpdate();
                }
                // One Minecraft profile can only point at one account; move it to this signup.
                try (PreparedStatement clearUuid = connection.prepareStatement(
                        "DELETE FROM account_minecraft_identities WHERE minecraft_uuid = ?")) {
                    clearUuid.setObject(1, minecraftUuid);
                    clearUuid.executeUpdate();
                }
                try (PreparedStatement identityStatement = connection.prepareStatement(insertIdentity)) {
                    identityStatement.setObject(1, identityId);
                    identityStatement.setObject(2, accountId);
                    identityStatement.setObject(3, minecraftUuid);
                    if (minecraftName == null) {
                        identityStatement.setNull(4, Types.VARCHAR);
                    } else {
                        identityStatement.setString(4, minecraftName);
                    }
                    identityStatement.setTimestamp(5, Timestamp.from(now));
                    identityStatement.setTimestamp(6, Timestamp.from(now));
                    identityStatement.executeUpdate();
                }
                JdbcMoneyRepository.insertWalletWithStartingBalance(
                        connection,
                        accountId,
                        MoneyService.STARTING_BALANCE,
                        MoneyService.STARTING_BALANCE_REASON,
                        now);
                connection.commit();
                return new Account(accountId, username, email, passwordHash, false, null, now, now, null);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    public void bindIdentity(UUID accountId, UUID minecraftUuid, @Nullable String minecraftName) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                INSERT INTO account_minecraft_identities
                    (id, account_id, minecraft_uuid, minecraft_name, bound_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, accountId);
            statement.setObject(3, minecraftUuid);
            if (minecraftName == null) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, minecraftName);
            }
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    /**
     * Makes {@code accountId} the sole owner of {@code minecraftUuid}.
     * Clears any previous binding for that UUID and moves/creates this account's identity row.
     * Used after password login so players can use their account from any Minecraft profile/machine.
     */
    public void rebindIdentityToMinecraftUuid(
            UUID accountId,
            UUID minecraftUuid,
            @Nullable String minecraftName
    ) throws SQLException {
        Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement clearUuid = connection.prepareStatement(
                        "DELETE FROM account_minecraft_identities WHERE minecraft_uuid = ?")) {
                    clearUuid.setObject(1, minecraftUuid);
                    clearUuid.executeUpdate();
                }

                Optional<AccountIdentity> existing = findIdentityByAccountId(connection, accountId);
                if (existing.isPresent()) {
                    String update = """
                            UPDATE account_minecraft_identities
                            SET minecraft_uuid = ?,
                                minecraft_name = COALESCE(?, minecraft_name),
                                last_seen_at = ?
                            WHERE account_id = ?
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(update)) {
                        statement.setObject(1, minecraftUuid);
                        if (minecraftName == null) {
                            statement.setNull(2, Types.VARCHAR);
                        } else {
                            statement.setString(2, minecraftName);
                        }
                        statement.setTimestamp(3, Timestamp.from(now));
                        statement.setObject(4, accountId);
                        statement.executeUpdate();
                    }
                } else {
                    String insert = """
                            INSERT INTO account_minecraft_identities
                                (id, account_id, minecraft_uuid, minecraft_name, bound_at, last_seen_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """;
                    try (PreparedStatement statement = connection.prepareStatement(insert)) {
                        statement.setObject(1, UUID.randomUUID());
                        statement.setObject(2, accountId);
                        statement.setObject(3, minecraftUuid);
                        if (minecraftName == null) {
                            statement.setNull(4, Types.VARCHAR);
                        } else {
                            statement.setString(4, minecraftName);
                        }
                        statement.setTimestamp(5, Timestamp.from(now));
                        statement.setTimestamp(6, Timestamp.from(now));
                        statement.executeUpdate();
                    }
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previous);
            }
        }
    }

    private Optional<AccountIdentity> findIdentityByAccountId(Connection connection, UUID accountId)
            throws SQLException {
        String sql = """
                SELECT id, account_id, minecraft_uuid, minecraft_name, bound_at, last_seen_at
                FROM account_minecraft_identities
                WHERE account_id = ?
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapIdentity(resultSet));
            }
        }
    }

    public void markEmailVerified(UUID accountId) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                UPDATE accounts
                SET email_verified = TRUE,
                    email_verified_at = ?,
                    updated_at = ?,
                    last_login_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setObject(4, accountId);
            statement.executeUpdate();
        }
    }

    public void updatePasswordHash(UUID accountId, String passwordHash) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                UPDATE accounts
                SET password_hash = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, passwordHash);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setObject(3, accountId);
            statement.executeUpdate();
        }
    }

    public void updateLastLogin(UUID accountId) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                UPDATE accounts
                SET last_login_at = ?, updated_at = ?
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setObject(3, accountId);
            statement.executeUpdate();
        }
    }

    public void updateIdentityLastSeen(UUID identityId, @Nullable String minecraftName) throws SQLException {
        Instant now = Instant.now();
        String sql = """
                UPDATE account_minecraft_identities
                SET last_seen_at = ?, minecraft_name = COALESCE(?, minecraft_name)
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            if (minecraftName == null) {
                statement.setNull(2, Types.VARCHAR);
            } else {
                statement.setString(2, minecraftName);
            }
            statement.setObject(3, identityId);
            statement.executeUpdate();
        }
    }

    private static Account mapAccount(ResultSet resultSet) throws SQLException {
        return new Account(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("username"),
                resultSet.getString("email"),
                resultSet.getString("password_hash"),
                resultSet.getBoolean("email_verified"),
                optionalInstant(resultSet.getTimestamp("email_verified_at")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                optionalInstant(resultSet.getTimestamp("last_login_at"))
        );
    }

    private static AccountIdentity mapIdentity(ResultSet resultSet) throws SQLException {
        return new AccountIdentity(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("account_id", UUID.class),
                resultSet.getObject("minecraft_uuid", UUID.class),
                resultSet.getString("minecraft_name"),
                resultSet.getTimestamp("bound_at").toInstant(),
                optionalInstant(resultSet.getTimestamp("last_seen_at"))
        );
    }

    private static @Nullable Instant optionalInstant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
