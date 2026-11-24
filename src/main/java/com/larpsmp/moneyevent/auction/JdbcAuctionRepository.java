package com.larpsmp.moneyevent.auction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;

/**
 * PostgreSQL persistence for auction sessions, representatives, lots, bids, and runtime.
 */
public final class JdbcAuctionRepository {

    private static final String RUNTIME_KEY = "default";

    private final DataSource dataSource;

    public JdbcAuctionRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsertSession(AuctionSession session) throws SQLException {
        String sql = """
                INSERT INTO auction_sessions (session_id, status, started_at, updated_at, closed_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (session_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at,
                    closed_at = EXCLUDED.closed_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, session.sessionId());
            statement.setString(2, session.status().name());
            statement.setTimestamp(3, Timestamp.from(session.startedAt()));
            statement.setTimestamp(4, Timestamp.from(session.updatedAt()));
            setInstant(statement, 5, session.closedAt());
            statement.executeUpdate();
        }
    }

    public Optional<AuctionSession> findSession(UUID sessionId) throws SQLException {
        String sql = """
                SELECT session_id, status, started_at, updated_at, closed_at
                FROM auction_sessions
                WHERE session_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSession(resultSet));
            }
        }
    }

    public void upsertRepresentative(AuctionRepresentative representative) throws SQLException {
        String sql = """
                INSERT INTO auction_representatives (session_id, team_id, account_id, registered_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (session_id, team_id) DO UPDATE SET
                    account_id = EXCLUDED.account_id,
                    registered_at = EXCLUDED.registered_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, representative.sessionId());
            statement.setObject(2, representative.teamId());
            statement.setObject(3, representative.accountId());
            statement.setTimestamp(4, Timestamp.from(representative.registeredAt()));
            statement.executeUpdate();
        }
    }

    public List<AuctionRepresentative> listRepresentatives(UUID sessionId) throws SQLException {
        String sql = """
                SELECT session_id, team_id, account_id, registered_at
                FROM auction_representatives
                WHERE session_id = ?
                ORDER BY registered_at ASC
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuctionRepresentative> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapRepresentative(resultSet));
                }
                return rows;
            }
        }
    }

    public Optional<AuctionRepresentative> findRepresentativeByAccount(UUID sessionId, UUID accountId)
            throws SQLException {
        String sql = """
                SELECT session_id, team_id, account_id, registered_at
                FROM auction_representatives
                WHERE session_id = ? AND account_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapRepresentative(resultSet));
            }
        }
    }

    public void upsertLot(AuctionLot lot) throws SQLException {
        String sql = """
                INSERT INTO auction_lots (
                    lot_id, session_id, lot_number, theme, starting_bid, status,
                    high_bid, high_bidder_account_id, owed_account_id, owed_amount,
                    payment_transaction_id, excluded_payer_ids,
                    opened_at, updated_at, closed_at, paid_at, delivered_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (lot_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    high_bid = EXCLUDED.high_bid,
                    high_bidder_account_id = EXCLUDED.high_bidder_account_id,
                    owed_account_id = EXCLUDED.owed_account_id,
                    owed_amount = EXCLUDED.owed_amount,
                    payment_transaction_id = EXCLUDED.payment_transaction_id,
                    excluded_payer_ids = EXCLUDED.excluded_payer_ids,
                    updated_at = EXCLUDED.updated_at,
                    closed_at = EXCLUDED.closed_at,
                    paid_at = EXCLUDED.paid_at,
                    delivered_at = EXCLUDED.delivered_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindLot(statement, lot);
            statement.executeUpdate();
        }
    }

    public Optional<AuctionLot> findLot(UUID lotId) throws SQLException {
        String sql = """
                SELECT lot_id, session_id, lot_number, theme, starting_bid, status,
                       high_bid, high_bidder_account_id, owed_account_id, owed_amount,
                       payment_transaction_id, excluded_payer_ids,
                       opened_at, updated_at, closed_at, paid_at, delivered_at
                FROM auction_lots
                WHERE lot_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, lotId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapLot(resultSet));
            }
        }
    }

    public int nextLotNumber(UUID sessionId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(lot_number), 0) + 1 FROM auction_lots WHERE session_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public void insertBid(AuctionBid bid) throws SQLException {
        String sql = """
                INSERT INTO auction_bids (bid_id, lot_id, account_id, team_id, amount, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, bid.bidId());
            statement.setObject(2, bid.lotId());
            statement.setObject(3, bid.accountId());
            statement.setObject(4, bid.teamId());
            statement.setLong(5, bid.amount());
            statement.setTimestamp(6, Timestamp.from(bid.createdAt()));
            statement.executeUpdate();
        }
    }

    public List<AuctionBid> listBidsChronological(UUID lotId) throws SQLException {
        String sql = """
                SELECT bid_id, lot_id, account_id, team_id, amount, created_at
                FROM auction_bids
                WHERE lot_id = ?
                ORDER BY created_at ASC, bid_id ASC
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, lotId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<AuctionBid> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(mapBid(resultSet));
                }
                return rows;
            }
        }
    }

    public void setRuntime(@Nullable UUID activeSessionId, @Nullable UUID activeLotId, Instant now)
            throws SQLException {
        String sql = """
                UPDATE auction_runtime
                SET active_session_id = ?, active_lot_id = ?, updated_at = ?
                WHERE singleton_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setUuid(statement, 1, activeSessionId);
            setUuid(statement, 2, activeLotId);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setString(4, RUNTIME_KEY);
            statement.executeUpdate();
        }
    }

    public void setPaymentUsername(String paymentUsername, Instant now) throws SQLException {
        Objects.requireNonNull(paymentUsername, "paymentUsername");
        String trimmed = paymentUsername.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("paymentUsername cannot be blank");
        }
        String sql = """
                UPDATE auction_runtime
                SET payment_username = ?, updated_at = ?
                WHERE singleton_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, trimmed);
            statement.setTimestamp(2, Timestamp.from(now));
            statement.setString(3, RUNTIME_KEY);
            statement.executeUpdate();
        }
    }

    public RuntimeState loadRuntime() throws SQLException {
        String sql = """
                SELECT active_session_id, active_lot_id, payment_username
                FROM auction_runtime
                WHERE singleton_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, RUNTIME_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new RuntimeState(null, null, AuctionConfig.DEFAULT_PAYMENT_USERNAME);
                }
                String paymentUsername = resultSet.getString("payment_username");
                if (paymentUsername == null || paymentUsername.isBlank()) {
                    paymentUsername = AuctionConfig.DEFAULT_PAYMENT_USERNAME;
                }
                return new RuntimeState(
                        resultSet.getObject("active_session_id", UUID.class),
                        resultSet.getObject("active_lot_id", UUID.class),
                        paymentUsername.trim());
            }
        }
    }

    private static void bindLot(PreparedStatement statement, AuctionLot lot) throws SQLException {
        statement.setObject(1, lot.lotId());
        statement.setObject(2, lot.sessionId());
        statement.setInt(3, lot.lotNumber());
        statement.setString(4, lot.theme());
        statement.setLong(5, lot.startingBid());
        statement.setString(6, lot.status().name());
        setLong(statement, 7, lot.highBid());
        setUuid(statement, 8, lot.highBidderAccountId());
        setUuid(statement, 9, lot.owedAccountId());
        setLong(statement, 10, lot.owedAmount());
        setUuid(statement, 11, lot.paymentTransactionId());
        statement.setString(12, lot.excludedPayerIdsSerialized());
        statement.setTimestamp(13, Timestamp.from(lot.openedAt()));
        statement.setTimestamp(14, Timestamp.from(lot.updatedAt()));
        setInstant(statement, 15, lot.closedAt());
        setInstant(statement, 16, lot.paidAt());
        setInstant(statement, 17, lot.deliveredAt());
    }

    private static AuctionSession mapSession(ResultSet resultSet) throws SQLException {
        return new AuctionSession(
                resultSet.getObject("session_id", UUID.class),
                AuctionSessionStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("started_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                optionalInstant(resultSet, "closed_at"));
    }

    private static AuctionRepresentative mapRepresentative(ResultSet resultSet) throws SQLException {
        return new AuctionRepresentative(
                resultSet.getObject("session_id", UUID.class),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getObject("account_id", UUID.class),
                resultSet.getTimestamp("registered_at").toInstant());
    }

    private static AuctionLot mapLot(ResultSet resultSet) throws SQLException {
        long highBid = resultSet.getLong("high_bid");
        Long highBidBoxed = resultSet.wasNull() ? null : highBid;
        long owedAmount = resultSet.getLong("owed_amount");
        Long owedAmountBoxed = resultSet.wasNull() ? null : owedAmount;
        return new AuctionLot(
                resultSet.getObject("lot_id", UUID.class),
                resultSet.getObject("session_id", UUID.class),
                resultSet.getInt("lot_number"),
                resultSet.getString("theme"),
                resultSet.getLong("starting_bid"),
                AuctionLotStatus.valueOf(resultSet.getString("status")),
                highBidBoxed,
                resultSet.getObject("high_bidder_account_id", UUID.class),
                resultSet.getObject("owed_account_id", UUID.class),
                owedAmountBoxed,
                resultSet.getObject("payment_transaction_id", UUID.class),
                resultSet.getString("excluded_payer_ids"),
                resultSet.getTimestamp("opened_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                optionalInstant(resultSet, "closed_at"),
                optionalInstant(resultSet, "paid_at"),
                optionalInstant(resultSet, "delivered_at"));
    }

    private static AuctionBid mapBid(ResultSet resultSet) throws SQLException {
        return new AuctionBid(
                resultSet.getObject("bid_id", UUID.class),
                resultSet.getObject("lot_id", UUID.class),
                resultSet.getObject("account_id", UUID.class),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getLong("amount"),
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

    private static void setInstant(PreparedStatement statement, int index, @Nullable Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    private static @Nullable Instant optionalInstant(ResultSet resultSet, String column)
            throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record RuntimeState(
            @Nullable UUID activeSessionId,
            @Nullable UUID activeLotId,
            String paymentUsername) {
    }
}
