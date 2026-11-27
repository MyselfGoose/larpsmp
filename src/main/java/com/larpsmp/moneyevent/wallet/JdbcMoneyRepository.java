package com.larpsmp.moneyevent.wallet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;

/**
 * PostgreSQL persistence for account-linked wallets and the money transaction ledger.
 */
public final class JdbcMoneyRepository {

    private static final String TRANSACTION_SELECT = """
            SELECT id, type, status, amount, reason, actor_account_id,
                   source_account_id, destination_account_id,
                   source_balance_before, source_balance_after,
                   destination_balance_before, destination_balance_after,
                   failure_reason, authorization_action, command, actor_username,
                   sender_type, created_at, parent_transaction_id,
                   external_ref_kind, external_ref_id
            FROM wallet_transactions
            """;

    private final DataSource dataSource;

    public JdbcMoneyRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    public Optional<WalletRow> findWalletByAccountId(UUID accountId) throws SQLException {
        String sql = """
                SELECT w.account_id, w.balance, w.starting_balance_granted, a.username,
                       i.minecraft_uuid, i.minecraft_name
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
                SELECT w.account_id, w.balance, w.starting_balance_granted, a.username,
                       i.minecraft_uuid, i.minecraft_name
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
                SELECT w.account_id, w.balance, w.starting_balance_granted, a.username,
                       i.minecraft_uuid, i.minecraft_name
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

    public List<WalletRow> loadCompetitorWalletsByBalanceDesc() throws SQLException {
        String sql = """
                SELECT w.account_id, w.balance, w.starting_balance_granted, a.username,
                       i.minecraft_uuid, i.minecraft_name
                FROM wallets w
                INNER JOIN accounts a ON a.id = w.account_id
                INNER JOIN account_participation p ON p.account_id = w.account_id AND p.competitor = TRUE
                LEFT JOIN account_minecraft_identities i ON i.account_id = w.account_id
                ORDER BY w.balance DESC, a.username ASC
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

    /**
     * Sum of personal wallet balances for every roster member of a team (offline included).
     * Members without a wallet row contribute 0.
     */
    public long sumBalancesForTeam(UUID teamId) throws SQLException {
        String sql = """
                SELECT COALESCE(SUM(w.balance), 0)
                FROM team_memberships m
                INNER JOIN account_participation p ON p.account_id = m.account_id AND p.competitor = TRUE
                LEFT JOIN wallets w ON w.account_id = m.account_id
                WHERE m.team_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, teamId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    public Optional<TransactionRecord> loadTransaction(UUID transactionId) throws SQLException {
        String sql = TRANSACTION_SELECT + " WHERE id = ?";
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
        String sql = TRANSACTION_SELECT + " ORDER BY created_at";
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

    public List<TransactionRecord> loadChildTransactions(UUID parentTransactionId) throws SQLException {
        String sql = TRANSACTION_SELECT + " WHERE parent_transaction_id = ? ORDER BY created_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, parentTransactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<TransactionRecord> records = new ArrayList<>();
                while (resultSet.next()) {
                    records.add(mapTransaction(resultSet));
                }
                return List.copyOf(records);
            }
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
        commitTransfer(
                sourceAccountId, sourceAfter, destinationAccountId, destinationAfter, record, null);
    }

    /**
     * Atomically updates two wallets, inserts the transfer ledger row, and optionally records a
     * settlement in the same database transaction.
     */
    public void commitTransfer(
            UUID sourceAccountId,
            long sourceAfter,
            UUID destinationAccountId,
            long destinationAfter,
            TransactionRecord record,
            @Nullable SettlementRow settlement) throws SQLException {
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
                if (settlement != null) {
                    insertSettlement(connection, settlement);
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

    /**
     * Multi-wallet settlement: lock wallets, apply balances, write ledger rows, record settlement,
     * optionally advance reward rotation.
     */
    public void commitSettlement(SettlementCommit commit) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            boolean previous = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<UUID> ordered = commit.balanceUpdates().keySet().stream()
                        .sorted(Comparator.naturalOrder())
                        .toList();
                for (UUID accountId : ordered) {
                    lockWallet(connection, accountId);
                }
                for (Map.Entry<UUID, Long> entry : commit.balanceUpdates().entrySet()) {
                    updateBalance(connection, entry.getKey(), entry.getValue());
                }
                for (TransactionRecord record : commit.transactions()) {
                    insertTransaction(connection, record);
                }
                for (SettlementRow settlement : commit.settlements()) {
                    insertSettlement(connection, settlement);
                }
                if (commit.newRotationIndex() != null) {
                    updateRotationIndex(connection, commit.newRotationIndex());
                }
                if (commit.markStartingBalanceGranted() != null) {
                    markStartingBalanceGranted(connection, commit.markStartingBalanceGranted());
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

    public void recordFailure(TransactionRecord record) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            insertTransaction(connection, record);
        }
    }

    public static void insertEmptyWallet(Connection connection, UUID accountId, Instant now)
            throws SQLException {
        String insertWallet = """
                INSERT INTO wallets (account_id, balance, starting_balance_granted, created_at, updated_at)
                VALUES (?, 0, FALSE, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insertWallet)) {
            statement.setObject(1, accountId);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setTimestamp(3, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    /**
     * @deprecated Prefer {@link #insertEmptyWallet} at signup and grant later for competitors.
     */
    @Deprecated
    public static void insertWalletWithStartingBalance(
            Connection connection,
            UUID accountId,
            long startingBalance,
            String reason,
            Instant now) throws SQLException {
        String insertWallet = """
                INSERT INTO wallets (account_id, balance, starting_balance_granted, created_at, updated_at)
                VALUES (?, ?, TRUE, ?, ?)
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

    public EconomySettingsRow loadSettings() throws SQLException {
        String sql = """
                SELECT kill_money_global, team_totals_visible, reward_rotation_index
                FROM economy_settings WHERE id = 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("economy_settings singleton row missing");
            }
            return new EconomySettingsRow(
                    resultSet.getBoolean("kill_money_global"),
                    resultSet.getBoolean("team_totals_visible"),
                    resultSet.getInt("reward_rotation_index"));
        }
    }

    public void updateKillMoneyGlobal(boolean enabled) throws SQLException {
        String sql = """
                UPDATE economy_settings
                SET kill_money_global = ?, updated_at = ?
                WHERE id = 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, enabled);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    public void updateTeamTotalsVisible(boolean visible) throws SQLException {
        String sql = """
                UPDATE economy_settings
                SET team_totals_visible = ?, updated_at = ?
                WHERE id = 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, visible);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    public void updateRotationIndex(Connection connection, int index) throws SQLException {
        String sql = """
                UPDATE economy_settings
                SET reward_rotation_index = ?, updated_at = ?
                WHERE id = 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, index);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    public void updateRotationIndex(int index) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            updateRotationIndex(connection, index);
        }
    }

    public Map<Integer, Long> loadKillPrices() throws SQLException {
        String sql = "SELECT tier, amount FROM economy_kill_prices ORDER BY tier";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            Map<Integer, Long> prices = new HashMap<>();
            while (resultSet.next()) {
                prices.put(resultSet.getInt("tier"), resultSet.getLong("amount"));
            }
            return Map.copyOf(prices);
        }
    }

    public void setKillPrice(int tier, long amount) throws SQLException {
        String sql = """
                INSERT INTO economy_kill_prices (tier, amount, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (tier) DO UPDATE
                SET amount = EXCLUDED.amount, updated_at = EXCLUDED.updated_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, tier);
            statement.setLong(2, amount);
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    public Optional<Boolean> findPlayerKillMoneyOverride(UUID accountId) throws SQLException {
        String sql = """
                SELECT enabled FROM economy_kill_money_player_overrides WHERE account_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSet.getBoolean("enabled"));
            }
        }
    }

    public void setPlayerKillMoneyOverride(UUID accountId, boolean enabled) throws SQLException {
        String sql = """
                INSERT INTO economy_kill_money_player_overrides (account_id, enabled, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE
                SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.setBoolean(2, enabled);
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    public void clearPlayerKillMoneyOverride(UUID accountId) throws SQLException {
        String sql = "DELETE FROM economy_kill_money_player_overrides WHERE account_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.executeUpdate();
        }
    }

    public Optional<Boolean> findEventKillMoney(String eventId) throws SQLException {
        String sql = "SELECT enabled FROM economy_kill_money_events WHERE event_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSet.getBoolean("enabled"));
            }
        }
    }

    public void setEventKillMoney(String eventId, boolean enabled) throws SQLException {
        String sql = """
                INSERT INTO economy_kill_money_events (event_id, enabled, updated_at)
                VALUES (?, ?, ?)
                ON CONFLICT (event_id) DO UPDATE
                SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            statement.setBoolean(2, enabled);
            statement.setTimestamp(3, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    public void addEventParticipant(String eventId, UUID accountId) throws SQLException {
        ensureEventExists(eventId);
        String sql = """
                INSERT INTO economy_kill_money_event_participants (event_id, account_id)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            statement.setObject(2, accountId);
            statement.executeUpdate();
        }
    }

    public void removeEventParticipant(String eventId, UUID accountId) throws SQLException {
        String sql = """
                DELETE FROM economy_kill_money_event_participants
                WHERE event_id = ? AND account_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            statement.setObject(2, accountId);
            statement.executeUpdate();
        }
    }

    public void clearEventParticipants(String eventId) throws SQLException {
        String sql = "DELETE FROM economy_kill_money_event_participants WHERE event_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            statement.executeUpdate();
        }
    }

    public List<UUID> listEventParticipants(String eventId) throws SQLException {
        String sql = """
                SELECT account_id FROM economy_kill_money_event_participants
                WHERE event_id = ? ORDER BY account_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<UUID> ids = new ArrayList<>();
                while (resultSet.next()) {
                    ids.add(resultSet.getObject("account_id", UUID.class));
                }
                return List.copyOf(ids);
            }
        }
    }

    public Optional<String> findEventContainingParticipant(UUID accountId) throws SQLException {
        String sql = """
                SELECT e.event_id
                FROM economy_kill_money_event_participants p
                INNER JOIN economy_kill_money_events e ON e.event_id = p.event_id
                WHERE p.account_id = ?
                ORDER BY e.updated_at DESC
                LIMIT 1
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSet.getString("event_id"));
            }
        }
    }

    public Optional<SettlementRow> findSettlement(String kind, String externalId) throws SQLException {
        String sql = """
                SELECT kind, external_id, outcome, parent_transaction_id, charged_total, detail_json, created_at
                FROM economy_settlements
                WHERE kind = ? AND external_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kind);
            statement.setString(2, externalId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSettlement(resultSet));
            }
        }
    }

    public void insertSettlementNoOp(String kind, String externalId, String outcome, String detail)
            throws SQLException {
        SettlementRow row = new SettlementRow(
                kind, externalId, outcome, null, 0L, detail, Instant.now());
        try (Connection connection = dataSource.getConnection()) {
            insertSettlement(connection, row);
        }
    }

    public void insertSettlementApplied(
            String kind,
            String externalId,
            @Nullable UUID parentTransactionId,
            long chargedTotal,
            String detail) throws SQLException {
        SettlementRow row = new SettlementRow(
                kind,
                externalId,
                EconomyRules.OUTCOME_APPLIED,
                parentTransactionId,
                chargedTotal,
                detail,
                Instant.now());
        try (Connection connection = dataSource.getConnection()) {
            insertSettlement(connection, row);
        }
    }

    public void insertAdminAudit(AdminAuditRow row) throws SQLException {
        String sql = """
                INSERT INTO economy_admin_audit (
                    id, action, scope, old_value, new_value, reason,
                    actor_username, sender_type, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, row.id());
            statement.setString(2, row.action());
            statement.setString(3, row.scope());
            statement.setString(4, row.oldValue());
            statement.setString(5, row.newValue());
            statement.setString(6, row.reason());
            statement.setString(7, row.actorUsername());
            statement.setString(8, row.senderType());
            statement.setTimestamp(9, Timestamp.from(row.createdAt()));
            statement.executeUpdate();
        }
    }

    private void ensureEventExists(String eventId) throws SQLException {
        String sql = """
                INSERT INTO economy_kill_money_events (event_id, enabled, updated_at)
                VALUES (?, TRUE, ?)
                ON CONFLICT (event_id) DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, eventId);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private static void markStartingBalanceGranted(Connection connection, UUID accountId)
            throws SQLException {
        String sql = """
                UPDATE wallets
                SET starting_balance_granted = TRUE, updated_at = ?
                WHERE account_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setObject(2, accountId);
            statement.executeUpdate();
        }
    }

    private static void insertSettlement(Connection connection, SettlementRow row) throws SQLException {
        String sql = """
                INSERT INTO economy_settlements (
                    kind, external_id, outcome, parent_transaction_id, charged_total, detail_json, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, row.kind());
            statement.setString(2, row.externalId());
            statement.setString(3, row.outcome());
            setUuid(statement, 4, row.parentTransactionId());
            statement.setLong(5, row.chargedTotal());
            statement.setString(6, row.detailJson());
            statement.setTimestamp(7, Timestamp.from(row.createdAt()));
            statement.executeUpdate();
        }
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

    static void insertTransaction(Connection connection, TransactionRecord record) throws SQLException {
        String sql = """
                INSERT INTO wallet_transactions (
                    id, type, status, amount, reason,
                    actor_account_id, source_account_id, destination_account_id,
                    source_balance_before, source_balance_after,
                    destination_balance_before, destination_balance_after,
                    failure_reason, authorization_action, command, actor_username,
                    sender_type, created_at, parent_transaction_id,
                    external_ref_kind, external_ref_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            setUuid(statement, 19, record.parentTransactionId());
            statement.setString(20, record.externalRefKind());
            statement.setString(21, record.externalRefId());
            statement.executeUpdate();
        }
    }

    private static WalletRow mapWalletRow(ResultSet resultSet) throws SQLException {
        return new WalletRow(
                resultSet.getObject("account_id", UUID.class),
                resultSet.getLong("balance"),
                resultSet.getBoolean("starting_balance_granted"),
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
                nullToEmpty(resultSet.getString("sender_type")),
                resultSet.getObject("parent_transaction_id", UUID.class),
                nullToEmpty(resultSet.getString("external_ref_kind")),
                nullToEmpty(resultSet.getString("external_ref_id")));
    }

    private static SettlementRow mapSettlement(ResultSet resultSet) throws SQLException {
        return new SettlementRow(
                resultSet.getString("kind"),
                resultSet.getString("external_id"),
                resultSet.getString("outcome"),
                resultSet.getObject("parent_transaction_id", UUID.class),
                resultSet.getLong("charged_total"),
                nullToEmpty(resultSet.getString("detail_json")),
                resultSet.getTimestamp("created_at").toInstant());
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
            boolean startingBalanceGranted,
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

    public record EconomySettingsRow(
            boolean killMoneyGlobal,
            boolean teamTotalsVisible,
            int rewardRotationIndex) {
    }

    public record SettlementRow(
            String kind,
            String externalId,
            String outcome,
            @Nullable UUID parentTransactionId,
            long chargedTotal,
            String detailJson,
            Instant createdAt) {
    }

    public record AdminAuditRow(
            UUID id,
            String action,
            String scope,
            String oldValue,
            String newValue,
            String reason,
            String actorUsername,
            String senderType,
            Instant createdAt) {
    }

    public record SettlementCommit(
            Map<UUID, Long> balanceUpdates,
            List<TransactionRecord> transactions,
            List<SettlementRow> settlements,
            @Nullable Integer newRotationIndex,
            @Nullable UUID markStartingBalanceGranted) {

        public SettlementCommit {
            balanceUpdates = Map.copyOf(balanceUpdates);
            transactions = List.copyOf(transactions);
            settlements = settlements == null ? List.of() : List.copyOf(settlements);
        }

        public static SettlementCommit of(
                Map<UUID, Long> balances,
                Collection<TransactionRecord> transactions,
                @Nullable SettlementRow settlement) {
            return ofSettlements(
                    balances,
                    transactions,
                    settlement == null ? List.of() : List.of(settlement));
        }

        public static SettlementCommit ofSettlements(
                Map<UUID, Long> balances,
                Collection<TransactionRecord> transactions,
                Collection<SettlementRow> settlements) {
            return new SettlementCommit(
                    balances, List.copyOf(transactions), List.copyOf(settlements), null, null);
        }

        public static SettlementCommit withRotation(
                Map<UUID, Long> balances,
                Collection<TransactionRecord> transactions,
                @Nullable SettlementRow settlement,
                @Nullable Integer newRotationIndex) {
            return new SettlementCommit(
                    balances,
                    List.copyOf(transactions),
                    settlement == null ? List.of() : List.of(settlement),
                    newRotationIndex,
                    null);
        }

        public static SettlementCommit withStartingBalanceGrant(
                Map<UUID, Long> balances,
                Collection<TransactionRecord> transactions,
                UUID accountId) {
            return new SettlementCommit(
                    balances, List.copyOf(transactions), List.of(), null, accountId);
        }
    }
}
