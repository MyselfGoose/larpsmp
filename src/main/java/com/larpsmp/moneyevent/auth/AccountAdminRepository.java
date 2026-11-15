package com.larpsmp.moneyevent.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Persistence for plugin admins identified by LarpSMP account id.
 */
public final class AccountAdminRepository {

    private final DataSource dataSource;

    public AccountAdminRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isAdmin(UUID accountId) throws SQLException {
        String sql = "SELECT 1 FROM account_plugin_admins WHERE account_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public boolean grant(UUID accountId, String grantedBy) throws SQLException {
        String sql = """
                INSERT INTO account_plugin_admins (account_id, granted_at, granted_by)
                VALUES (?, ?, ?)
                ON CONFLICT (account_id) DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setString(3, grantedBy == null ? "" : grantedBy);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean revoke(UUID accountId) throws SQLException {
        String sql = "DELETE FROM account_plugin_admins WHERE account_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, accountId);
            return statement.executeUpdate() > 0;
        }
    }

    public List<AdminRow> listAdmins() throws SQLException {
        String sql = """
                SELECT a.id, a.username, p.granted_at, p.granted_by
                FROM account_plugin_admins p
                INNER JOIN accounts a ON a.id = p.account_id
                ORDER BY LOWER(a.username)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<AdminRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new AdminRow(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("username"),
                        resultSet.getTimestamp("granted_at").toInstant(),
                        resultSet.getString("granted_by")));
            }
            return List.copyOf(rows);
        }
    }

    public record AdminRow(UUID accountId, String username, Instant grantedAt, String grantedBy) {
    }
}
