package com.larpsmp.moneyevent.capture;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.jetbrains.annotations.Nullable;

/**
 * PostgreSQL persistence for capture sessions, day completions, temp blocks, and runtime pointer.
 */
public final class JdbcCaptureRepository {

    private static final String RUNTIME_KEY = "default";

    private final DataSource dataSource;

    public JdbcCaptureRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void upsertSession(CaptureSession session) throws SQLException {
        String sql = """
                INSERT INTO capture_sessions (
                    capture_id, point_id, status, owning_team_id, progress_millis,
                    reference_full_millis, announced_thresholds, day_key, winning_team_id,
                    settlement_outcome, started_at, updated_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (capture_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    owning_team_id = EXCLUDED.owning_team_id,
                    progress_millis = EXCLUDED.progress_millis,
                    reference_full_millis = EXCLUDED.reference_full_millis,
                    announced_thresholds = EXCLUDED.announced_thresholds,
                    winning_team_id = EXCLUDED.winning_team_id,
                    settlement_outcome = EXCLUDED.settlement_outcome,
                    updated_at = EXCLUDED.updated_at,
                    completed_at = EXCLUDED.completed_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindSession(statement, session);
            statement.executeUpdate();
        }
    }

    public Optional<CaptureSession> findSession(UUID captureId) throws SQLException {
        String sql = """
                SELECT capture_id, point_id, status, owning_team_id, progress_millis,
                       reference_full_millis, announced_thresholds, day_key, winning_team_id,
                       settlement_outcome, started_at, updated_at, completed_at
                FROM capture_sessions
                WHERE capture_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, captureId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapSession(resultSet));
            }
        }
    }

    public void setRuntime(@Nullable UUID activeCaptureId, boolean paused, Instant now)
            throws SQLException {
        String sql = """
                UPDATE capture_runtime
                SET active_capture_id = ?, paused = ?, updated_at = ?
                WHERE singleton_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (activeCaptureId == null) {
                statement.setNull(1, Types.OTHER);
            } else {
                statement.setObject(1, activeCaptureId);
            }
            statement.setBoolean(2, paused);
            statement.setTimestamp(3, Timestamp.from(now));
            statement.setString(4, RUNTIME_KEY);
            statement.executeUpdate();
        }
    }

    public RuntimeState loadRuntime() throws SQLException {
        String sql = """
                SELECT active_capture_id, paused
                FROM capture_runtime
                WHERE singleton_key = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, RUNTIME_KEY);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return new RuntimeState(null, false);
                }
                return new RuntimeState(
                        resultSet.getObject("active_capture_id", UUID.class),
                        resultSet.getBoolean("paused")
                );
            }
        }
    }

    public void recordDayCompletion(
            String dayKey,
            String pointId,
            UUID captureId,
            UUID winningTeamId,
            Instant completedAt) throws SQLException {
        String sql = """
                INSERT INTO capture_day_completions (day_key, point_id, capture_id, winning_team_id, completed_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (day_key, point_id) DO NOTHING
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dayKey);
            statement.setString(2, pointId);
            statement.setObject(3, captureId);
            statement.setObject(4, winningTeamId);
            statement.setTimestamp(5, Timestamp.from(completedAt));
            statement.executeUpdate();
        }
    }

    public Set<String> completedPointIds(String dayKey) throws SQLException {
        String sql = "SELECT point_id FROM capture_day_completions WHERE day_key = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dayKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                Set<String> ids = new HashSet<>();
                while (resultSet.next()) {
                    ids.add(resultSet.getString("point_id"));
                }
                return Set.copyOf(ids);
            }
        }
    }

    public void upsertTempBlock(CaptureTempBlock block) throws SQLException {
        String sql = """
                INSERT INTO capture_temp_blocks (
                    world, x, y, z, prior_block_data, placed_material, placed_at, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (world, x, y, z) DO UPDATE SET
                    prior_block_data = EXCLUDED.prior_block_data,
                    placed_material = EXCLUDED.placed_material,
                    placed_at = EXCLUDED.placed_at,
                    expires_at = EXCLUDED.expires_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, block.world());
            statement.setInt(2, block.x());
            statement.setInt(3, block.y());
            statement.setInt(4, block.z());
            statement.setString(5, block.priorBlockData());
            statement.setString(6, block.placedMaterial());
            statement.setTimestamp(7, Timestamp.from(block.placedAt()));
            statement.setTimestamp(8, Timestamp.from(block.expiresAt()));
            statement.executeUpdate();
        }
    }

    public Optional<CaptureTempBlock> findTempBlock(String world, int x, int y, int z) throws SQLException {
        String sql = """
                SELECT world, x, y, z, prior_block_data, placed_material, placed_at, expires_at
                FROM capture_temp_blocks
                WHERE world = ? AND x = ? AND y = ? AND z = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, world);
            statement.setInt(2, x);
            statement.setInt(3, y);
            statement.setInt(4, z);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapTempBlock(resultSet));
            }
        }
    }

    public void deleteTempBlock(String world, int x, int y, int z) throws SQLException {
        String sql = "DELETE FROM capture_temp_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, world);
            statement.setInt(2, x);
            statement.setInt(3, y);
            statement.setInt(4, z);
            statement.executeUpdate();
        }
    }

    public List<CaptureTempBlock> listTempBlocks() throws SQLException {
        String sql = """
                SELECT world, x, y, z, prior_block_data, placed_material, placed_at, expires_at
                FROM capture_temp_blocks
                ORDER BY expires_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<CaptureTempBlock> blocks = new ArrayList<>();
            while (resultSet.next()) {
                blocks.add(mapTempBlock(resultSet));
            }
            return List.copyOf(blocks);
        }
    }

    public List<CaptureTempBlock> listExpiredTempBlocks(Instant now) throws SQLException {
        String sql = """
                SELECT world, x, y, z, prior_block_data, placed_material, placed_at, expires_at
                FROM capture_temp_blocks
                WHERE expires_at <= ?
                ORDER BY expires_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(now));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<CaptureTempBlock> blocks = new ArrayList<>();
                while (resultSet.next()) {
                    blocks.add(mapTempBlock(resultSet));
                }
                return List.copyOf(blocks);
            }
        }
    }

    private static void bindSession(PreparedStatement statement, CaptureSession session) throws SQLException {
        statement.setObject(1, session.captureId());
        statement.setString(2, session.pointId());
        statement.setString(3, session.status().name());
        if (session.progress().owningTeamId() == null) {
            statement.setNull(4, Types.OTHER);
        } else {
            statement.setObject(4, session.progress().owningTeamId());
        }
        statement.setLong(5, session.progress().progressMillis());
        statement.setLong(6, session.progress().referenceFullMillis());
        statement.setString(7, session.progress().announcedThresholdsCsv());
        statement.setString(8, session.dayKey());
        if (session.winningTeamId() == null) {
            statement.setNull(9, Types.OTHER);
        } else {
            statement.setObject(9, session.winningTeamId());
        }
        statement.setString(10, session.settlementOutcome());
        statement.setTimestamp(11, Timestamp.from(session.startedAt()));
        statement.setTimestamp(12, Timestamp.from(session.updatedAt()));
        if (session.completedAt() == null) {
            statement.setNull(13, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(13, Timestamp.from(session.completedAt()));
        }
    }

    private static CaptureSession mapSession(ResultSet resultSet) throws SQLException {
        long referenceFull = resultSet.getLong("reference_full_millis");
        CaptureProgressState progress = new CaptureProgressState(
                resultSet.getObject("owning_team_id", UUID.class),
                resultSet.getLong("progress_millis"),
                referenceFull,
                CaptureProgressState.parseAnnouncedThresholds(resultSet.getString("announced_thresholds"))
        );
        Timestamp started = resultSet.getTimestamp("started_at");
        Timestamp updated = resultSet.getTimestamp("updated_at");
        Timestamp completed = resultSet.getTimestamp("completed_at");
        return new CaptureSession(
                resultSet.getObject("capture_id", UUID.class),
                resultSet.getString("point_id"),
                resultSet.getString("day_key"),
                CaptureSessionStatus.valueOf(resultSet.getString("status")),
                progress,
                resultSet.getObject("winning_team_id", UUID.class),
                resultSet.getString("settlement_outcome"),
                started.toInstant(),
                updated.toInstant(),
                completed == null ? null : completed.toInstant()
        );
    }

    private static CaptureTempBlock mapTempBlock(ResultSet resultSet) throws SQLException {
        return new CaptureTempBlock(
                resultSet.getString("world"),
                resultSet.getInt("x"),
                resultSet.getInt("y"),
                resultSet.getInt("z"),
                resultSet.getString("prior_block_data"),
                resultSet.getString("placed_material"),
                resultSet.getTimestamp("placed_at").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant()
        );
    }

    public record RuntimeState(@Nullable UUID activeCaptureId, boolean paused) {
    }
}
