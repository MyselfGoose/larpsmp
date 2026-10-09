package com.larpsmp.moneyevent.wallet;

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
 * PostgreSQL persistence for account-linked wallets and the money transaction ledger.
 */
public final class JdbcMoneyRepository {

    private final DataSource dataSource;

    public JdbcMoneyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<WalletRow> findWalletByAccountId(UUID accountId) throws SQLException {
        String sql = """
                SELECT w.account_id, w.balance, a.username, i.minecraft_uuid, i.minecraft_name
                FROM wallets w
                INNER JOIN accounts a ON a.id = w.account_id
                LEFT JOIN account_minecraft_identities i ON i.account_id = w.account_id
                WHERE w.account_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapWalletRow(resultSet));
            }
        }
    }

    public Optional<WalletRow> findWalletByMinecraftUuid(UUID minecraftUuid) throws SQLException {
        String sql = """
                SELECT w.account_id, w.balance, a.username, i.minecraft_uuid, i.minecraft_name
                FROM wallets w
                INNER JOIN accounts a ON a.id = w.account_id
                INNER JOIN account_minecraft_identities i ON i.account_id = w.account_id
                WHERE i.minecraft_uuid = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, minecraftUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapWalletRow(resultSet));
            }
        }
    }

    public List<WalletRow> loadAllWallets() throws SQLException {
        String sql = """
                SELECT w.account_id, w.balance, a.username, i.minecraft_uuid, i.minecraft_name
                FROM wallets w
                INNER JOIN accounts a ON a.id = w.account_id
                LEFT JOIN account_minecraft_identities i ON i.account_id = w.account_id
                ORDER BY a.username
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<WalletRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(mapWalletRow(resultSet));
            }
            return List.copyOf(rows);
        }
    }

    public Optional<TransactionRecord> loadTransaction(UUID transactionId) throws SQLException {
        String sql = """
                SELECT id, type, status, amount, reason, actor_account_id,
                       source_account_id, destination_account_id,
                       source_balance_before, source_balance_after,
                       destination_balance_before, destination_balance_after,
                       failure_reason, authorization_action, command, actor_username,
                       sender_type, created_at
                FROM wallet_transactions
                WHERE id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTransaction(resultSet));
            }
        }
    }

    public List<TransactionRecord> loadAllTransactions() throws SQLException {
        String sql = """
                SELECT id, type, status, amount, reason, actor_account_id,
                       source_account_id, destination_account_id,
                       source_balance_before, source_balance_after,
                       destination_balance_before, destination_balance_after,
                       failure_reason, authorization_action, command, actor_username,
                       sender_type, created_at
                FROM wallet_transactions
                ORDER BY created_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<TransactionRecord> records = new ArrayList<>();
            while (resultSet.next()) {
                records.add(mapTransaction(resultSet));
            }
            return List.copyOf(records);
        }
    }

    /**
     * Atomically updates one wallet balance and inserts the ledger row.
     *
     * @return updated balance after the change
     */
    public long commitSingleWalletChange(UUID accountId, long newBalance, TransactionRecord record)
            throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                lockWallet(connection, accountId);
                updateBalance(connection, accountId, newBalance);
                insertTransaction(connection, record);
                connection.commit();
                return newBalance;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previous);
            }
        }
    }

    /**
     * Atomically updates two wallet balances and inserts the transfer ledger row.
     */
    public void commitTransfer(
            UUID sourceAccountId,
            long sourceAfter,
            UUID destinationAccountId,
            long destinationAfter,
            TransactionRecord record) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                // Lock in UUID order to avoid deadlocks under concurrent transfers.
                if (sourceAccountId.compareTo(destinationAccountId) < 0) {
                    lockWallet(connection, sourceAccountId);
                    lockWallet(connection, destinationAccountId);
                } else {
                    lockWallet(connection, destinationAccountId);
                    lockWallet(connection, sourceAccountId);
                }
                updateBalance(connection, sourceAccountId, sourceAfter);
                updateBalance(connection, destinationAccountId, destinationAfter);
                insertTransaction(connection, record);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(previous);
            }
        }
    }

    public void recordFailure(TransactionRecord record) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            insertTransaction(connection, record);
        }
    }

    public static void insertWalletWithStartingBalance(
            Connection connection,
            UUID accountId,
            long startingBalance,
            String reason,
            Instant now) throws SQLException {
        String insertWallet = """
                INSERT INTO wallets (account_id, balance, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insertWallet)) {
            statement.setObject(1, accountId);
            statement.setLong(2, startingBalance);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setTimestamp(4, Timestamp.from(now));
            statement.executeUpdate();
        }

        TransactionRecord starting = new TransactionRecord(
                UUID.randomUUID(),
                now,
                TransactionType.STARTING_BALANCE,
                TransactionStatus.SUCCESS,
                startingBalance,
                reason,
                null,
                accountId,
                null,
                0L,
                startingBalance,
                null,
                null,
                "");
        insertTransaction(connection, starting);
    }

    private static void lockWallet(Connection connection, UUID accountId) throws SQLException {
        String sql = "SELECT balance FROM wallets WHERE account_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Wallet not found for account " + accountId);
                }
            }
        }
    }

    private static void updateBalance(Connection connection, UUID accountId, long balance) throws SQLException {
        String sql = """
                UPDATE wallets
                SET balance = ?, updated_at = ?
                WHERE account_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, balance);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setObject(3, accountId);
            int updated = statement.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Expected to update 1 wallet for " + accountId + ", updated " + updated);
            }
        }
    }

    private static void insertTransaction(Connection connection, TransactionRecord record) throws SQLException {
        String sql = """
                INSERT INTO wallet_transactions (
                    id, type, status, amount, reason,
                    actor_account_id, source_account_id, destination_account_id,
                    source_balance_before, source_balance_after,
                    destination_balance_before, destination_balance_after,
                    failure_reason, authorization_action, command, actor_username,
                    sender_type, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, record.transactionId());
            statement.setString(2, record.type().name());
            statement.setString(3, record.status().name());
            statement.setLong(4, record.amount());
            statement.setString(5, record.reason());
            setUuid(statement, 6, record.actorId());
            setUuid(statement, 7, record.sourceWalletId());
            setUuid(statement, 8, record.destinationWalletId());
            setLong(statement, 9, record.sourceBalanceBefore());
            setLong(statement, 10, record.sourceBalanceAfter());
            setLong(statement, 11, record.destinationBalanceBefore());
            setLong(statement, 12, record.destinationBalanceAfter());
            statement.setString(13, record.failureReason());
            statement.setString(14, record.authorizationAction());
            statement.setString(15, record.command());
            statement.setString(16, record.actorUsername());
            statement.setString(17, record.senderType());
            statement.setTimestamp(18, Timestamp.from(record.timestamp()));
            statement.executeUpdate();
        }
    }

    private static WalletRow mapWalletRow(ResultSet resultSet) throws SQLException {
        return new WalletRow(
                resultSet.getObject("account_id", UUID.class),
                resultSet.getLong("balance"),
                resultSet.getString("username"),
                resultSet.getObject("minecraft_uuid", UUID.class),
                resultSet.getString("minecraft_name"));
    }

    private static TransactionRecord mapTransaction(ResultSet resultSet) throws SQLException {
        return new TransactionRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                TransactionType.valueOf(resultSet.getString("type")),
                TransactionStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("amount"),
                resultSet.getString("reason"),
                resultSet.getObject("actor_account_id", UUID.class),
                resultSet.getObject("source_account_id", UUID.class),
                resultSet.getObject("destination_account_id", UUID.class),
                getLong(resultSet, "source_balance_before"),
                getLong(resultSet, "source_balance_after"),
                getLong(resultSet, "destination_balance_before"),
                getLong(resultSet, "destination_balance_after"),
                resultSet.getString("failure_reason"),
                nullToEmpty(resultSet.getString("authorization_action")),
                nullToEmpty(resultSet.getString("command")),
                nullToEmpty(resultSet.getString("actor_username")),
                nullToEmpty(resultSet.getString("sender_type")));
    }

    private static void setUuid(PreparedStatement statement, int index, @Nullable UUID value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }

    private static void setLong(PreparedStatement statement, int index, @Nullable Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private static @Nullable Long getLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /**
     * Wallet joined with account/identity metadata for lookups and HUD.
     */
    public record WalletRow(
            UUID accountId,
            long balance,
            String username,
            @Nullable UUID minecraftUuid,
            @Nullable String minecraftName) {

        /**
         * Player-facing name is the LarpSMP account username. Minecraft client name is bind metadata only.
         */
        public String displayName() {
            if (username != null && !username.isBlank()) {
                return username;
            }
            if (minecraftName != null && !minecraftName.isBlank()) {
                return minecraftName;
            }
            return "unknown";
        }
    }
}
