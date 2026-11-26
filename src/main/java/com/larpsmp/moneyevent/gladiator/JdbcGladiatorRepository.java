package com.larpsmp.moneyevent.gladiator;

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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * JDBC persistence for Gladiators sessions, kits, and barrier undo rows.
 */
public final class JdbcGladiatorRepository {

    private final DataSource dataSource;

    public JdbcGladiatorRepository(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    public void upsertSession(GladiatorSession session) throws SQLException {
        String sql = """
                INSERT INTO gladiator_sessions (
                    session_id, phase, schedule_index, payout_completed, created_at, updated_at, ended_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id) DO UPDATE SET
                    phase = EXCLUDED.phase,
                    schedule_index = EXCLUDED.schedule_index,
                    payout_completed = EXCLUDED.payout_completed,
                    updated_at = EXCLUDED.updated_at,
                    ended_at = EXCLUDED.ended_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, session.sessionId());
            statement.setString(2, session.phase().name());
            statement.setInt(3, session.scheduleIndex());
            statement.setBoolean(4, session.payoutCompleted());
            statement.setTimestamp(5, Timestamp.from(session.createdAt()));
            statement.setTimestamp(6, Timestamp.from(session.updatedAt()));
            if (session.endedAt() == null) {
                statement.setNull(7, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(7, Timestamp.from(session.endedAt()));
            }
            statement.executeUpdate();
        }
    }

    public void setRuntime(UUID sessionId, Instant now) throws SQLException {
        String sql = """
                INSERT INTO gladiator_runtime (singleton_key, active_session_id, updated_at)
                VALUES ('default', ?, ?)
                ON CONFLICT (singleton_key) DO UPDATE SET
                    active_session_id = EXCLUDED.active_session_id,
                    updated_at = EXCLUDED.updated_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (sessionId == null) {
                statement.setNull(1, Types.OTHER);
            } else {
                statement.setObject(1, sessionId);
            }
            statement.setTimestamp(2, Timestamp.from(now));
            statement.executeUpdate();
        }
    }

    public Optional<UUID> loadActiveSessionId() throws SQLException {
        String sql = "SELECT active_session_id FROM gladiator_runtime WHERE singleton_key = 'default'";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                return Optional.empty();
            }
            Object value = resultSet.getObject("active_session_id");
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of((UUID) value);
        }
    }

    public Optional<GladiatorSession> findSession(UUID sessionId) throws SQLException {
        String sql = """
                SELECT session_id, phase, schedule_index, payout_completed, created_at, updated_at, ended_at
                FROM gladiator_sessions
                WHERE session_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                GladiatorSession session = mapSession(resultSet);
                hydrate(session);
                return Optional.of(session);
            }
        }
    }

    public void replaceSchedule(UUID sessionId, List<GladiatorScheduledEntry> entries) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM gladiator_schedule WHERE session_id = ?")) {
                    delete.setObject(1, sessionId);
                    delete.executeUpdate();
                }
                String insert = """
                        INSERT INTO gladiator_schedule (session_id, bout_index, team_a_id, team_b_id, status)
                        VALUES (?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    for (GladiatorScheduledEntry entry : entries) {
                        statement.setObject(1, sessionId);
                        statement.setInt(2, entry.boutIndex());
                        statement.setObject(3, entry.teamAId());
                        statement.setObject(4, entry.teamBId());
                        statement.setString(5, entry.status().name());
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

    public void upsertScheduleEntry(UUID sessionId, GladiatorScheduledEntry entry) throws SQLException {
        String sql = """
                INSERT INTO gladiator_schedule (session_id, bout_index, team_a_id, team_b_id, status)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (session_id, bout_index) DO UPDATE SET
                    status = EXCLUDED.status
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setInt(2, entry.boutIndex());
            statement.setObject(3, entry.teamAId());
            statement.setObject(4, entry.teamBId());
            statement.setString(5, entry.status().name());
            statement.executeUpdate();
        }
    }

    public void upsertFight(UUID sessionId, GladiatorFight fight) throws SQLException {
        String sql = """
                INSERT INTO gladiator_fights (
                    fight_id, session_id, fight_kind, bout_index, team_a_id, team_b_id,
                    kit_id, status, winning_team_id, started_at, ended_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (fight_id) DO UPDATE SET
                    status = EXCLUDED.status,
                    winning_team_id = EXCLUDED.winning_team_id,
                    ended_at = EXCLUDED.ended_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, fight.fightId());
            statement.setObject(2, sessionId);
            statement.setString(3, fight.kind().name());
            if (fight.boutIndex() == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, fight.boutIndex());
            }
            setUuidOrNull(statement, 5, fight.teamAId());
            setUuidOrNull(statement, 6, fight.teamBId());
            statement.setString(7, fight.kitId());
            statement.setString(8, fight.status().name());
            setUuidOrNull(statement, 9, fight.winningTeamId());
            statement.setTimestamp(10, Timestamp.from(fight.startedAt()));
            if (fight.endedAt() == null) {
                statement.setNull(11, Types.TIMESTAMP);
            } else {
                statement.setTimestamp(11, Timestamp.from(fight.endedAt()));
            }
            statement.executeUpdate();
        }
        replaceFighters(fight);
    }

    public void replaceLineups(UUID sessionId, Map<UUID, Map<Integer, UUID>> lineups) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM gladiator_lineups WHERE session_id = ?")) {
                    delete.setObject(1, sessionId);
                    delete.executeUpdate();
                }
                String insert = """
                        INSERT INTO gladiator_lineups (session_id, team_id, slot_index, account_id)
                        VALUES (?, ?, ?, ?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    for (Map.Entry<UUID, Map<Integer, UUID>> teamEntry : lineups.entrySet()) {
                        for (Map.Entry<Integer, UUID> slot : teamEntry.getValue().entrySet()) {
                            statement.setObject(1, sessionId);
                            statement.setObject(2, teamEntry.getKey());
                            statement.setInt(3, slot.getKey());
                            statement.setObject(4, slot.getValue());
                            statement.addBatch();
                        }
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

    public void upsertQuota(UUID sessionId, UUID accountId, int fightsUsed) throws SQLException {
        String sql = """
                INSERT INTO gladiator_quota (session_id, account_id, fights_used)
                VALUES (?, ?, ?)
                ON CONFLICT (session_id, account_id) DO UPDATE SET
                    fights_used = EXCLUDED.fights_used
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, accountId);
            statement.setInt(3, fightsUsed);
            statement.executeUpdate();
        }
    }

    public void clearKitBans(UUID sessionId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM gladiator_kit_bans WHERE session_id = ?")) {
            statement.setObject(1, sessionId);
            statement.executeUpdate();
        }
    }

    public void upsertKitBan(UUID sessionId, int banOrder, String kitId, UUID bannedBy) throws SQLException {
        String sql = """
                INSERT INTO gladiator_kit_bans (session_id, ban_order, kit_id, banned_by_account_id)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (session_id, ban_order) DO UPDATE SET
                    kit_id = EXCLUDED.kit_id,
                    banned_by_account_id = EXCLUDED.banned_by_account_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setInt(2, banOrder);
            statement.setString(3, kitId);
            statement.setObject(4, bannedBy);
            statement.executeUpdate();
        }
    }

    public void upsertDuelPending(UUID sessionId, GladiatorDuelPending pending) throws SQLException {
        String sql = """
                INSERT INTO gladiator_duel_pending (
                    session_id, fighter_a_account_id, fighter_b_account_id,
                    fighter_a_team_id, fighter_b_team_id, next_ban_account_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id) DO UPDATE SET
                    fighter_a_account_id = EXCLUDED.fighter_a_account_id,
                    fighter_b_account_id = EXCLUDED.fighter_b_account_id,
                    fighter_a_team_id = EXCLUDED.fighter_a_team_id,
                    fighter_b_team_id = EXCLUDED.fighter_b_team_id,
                    next_ban_account_id = EXCLUDED.next_ban_account_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            statement.setObject(2, pending.fighterAAccountId());
            statement.setObject(3, pending.fighterBAccountId());
            statement.setObject(4, pending.fighterATeamId());
            statement.setObject(5, pending.fighterBTeamId());
            statement.setObject(6, pending.nextBanAccountId());
            statement.executeUpdate();
        }
    }

    public void clearDuelPending(UUID sessionId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM gladiator_duel_pending WHERE session_id = ?")) {
            statement.setObject(1, sessionId);
            statement.executeUpdate();
        }
    }

    public void upsertBodySnapshot(UUID sessionId, GladiatorBodySnapshot snapshot) throws SQLException {
        String sql = """
                INSERT INTO gladiator_body_snapshots (
                    session_id, account_id, world_name, x, y, z, yaw, pitch,
                    inventory_data, armor_data, offhand_data, ender_chest_data,
                    health, food_level, saturation, exhaustion, level, total_experience, exp,
                    game_mode, potion_effects_data
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (session_id, account_id) DO UPDATE SET
                    world_name = EXCLUDED.world_name,
                    x = EXCLUDED.x,
                    y = EXCLUDED.y,
                    z = EXCLUDED.z,
                    yaw = EXCLUDED.yaw,
                    pitch = EXCLUDED.pitch,
                    inventory_data = EXCLUDED.inventory_data,
                    armor_data = EXCLUDED.armor_data,
                    offhand_data = EXCLUDED.offhand_data,
                    ender_chest_data = EXCLUDED.ender_chest_data,
                    health = EXCLUDED.health,
                    food_level = EXCLUDED.food_level,
                    saturation = EXCLUDED.saturation,
                    exhaustion = EXCLUDED.exhaustion,
                    level = EXCLUDED.level,
                    total_experience = EXCLUDED.total_experience,
                    exp = EXCLUDED.exp,
                    game_mode = EXCLUDED.game_mode,
                    potion_effects_data = EXCLUDED.potion_effects_data
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
            statement.setBytes(9, snapshot.inventoryData());
            statement.setBytes(10, snapshot.armorData());
            statement.setBytes(11, snapshot.offhandData());
            statement.setBytes(12, snapshot.enderChestData());
            statement.setDouble(13, snapshot.health());
            statement.setInt(14, snapshot.foodLevel());
            statement.setFloat(15, snapshot.saturation());
            statement.setFloat(16, snapshot.exhaustion());
            statement.setInt(17, snapshot.level());
            statement.setInt(18, snapshot.totalExperience());
            statement.setFloat(19, snapshot.exp());
            statement.setString(20, snapshot.gameMode());
            statement.setBytes(21, snapshot.potionEffectsData());
            statement.executeUpdate();
        }
    }

    public void upsertKit(GladiatorKit kit) throws SQLException {
        String sql = """
                INSERT INTO gladiator_kits (kit_id, inventory_data, armor_data, offhand_data, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (kit_id) DO UPDATE SET
                    inventory_data = EXCLUDED.inventory_data,
                    armor_data = EXCLUDED.armor_data,
                    offhand_data = EXCLUDED.offhand_data,
                    updated_at = EXCLUDED.updated_at
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kit.kitId());
            statement.setBytes(2, kit.inventoryData());
            statement.setBytes(3, kit.armorData());
            statement.setBytes(4, kit.offhandData());
            statement.setTimestamp(5, Timestamp.from(kit.updatedAt()));
            statement.executeUpdate();
        }
    }

    public void deleteKit(String kitId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM gladiator_kits WHERE kit_id = ?")) {
            statement.setString(1, kitId);
            statement.executeUpdate();
        }
    }

    public Optional<GladiatorKit> findKit(String kitId) throws SQLException {
        String sql = """
                SELECT kit_id, inventory_data, armor_data, offhand_data, updated_at
                FROM gladiator_kits WHERE kit_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, kitId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GladiatorKit(
                        resultSet.getString("kit_id"),
                        resultSet.getBytes("inventory_data"),
                        resultSet.getBytes("armor_data"),
                        resultSet.getBytes("offhand_data"),
                        resultSet.getTimestamp("updated_at").toInstant()
                ));
            }
        }
    }

    public List<GladiatorKit> listKits() throws SQLException {
        String sql = """
                SELECT kit_id, inventory_data, armor_data, offhand_data, updated_at
                FROM gladiator_kits ORDER BY kit_id
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<GladiatorKit> kits = new ArrayList<>();
            while (resultSet.next()) {
                kits.add(new GladiatorKit(
                        resultSet.getString("kit_id"),
                        resultSet.getBytes("inventory_data"),
                        resultSet.getBytes("armor_data"),
                        resultSet.getBytes("offhand_data"),
                        resultSet.getTimestamp("updated_at").toInstant()
                ));
            }
            return kits;
        }
    }

    public void replaceBarrierBlocks(List<GladiatorBarrierBlock> blocks) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM gladiator_barrier_blocks")) {
                    delete.executeUpdate();
                }
                String insert = """
                        INSERT INTO gladiator_barrier_blocks (world_name, x, y, z, prior_block_data)
                        VALUES (?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    for (GladiatorBarrierBlock block : blocks) {
                        statement.setString(1, block.worldName());
                        statement.setInt(2, block.x());
                        statement.setInt(3, block.y());
                        statement.setInt(4, block.z());
                        statement.setString(5, block.priorBlockData());
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

    public List<GladiatorBarrierBlock> loadBarrierBlocks() throws SQLException {
        String sql = """
                SELECT world_name, x, y, z, prior_block_data
                FROM gladiator_barrier_blocks
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<GladiatorBarrierBlock> blocks = new ArrayList<>();
            while (resultSet.next()) {
                blocks.add(new GladiatorBarrierBlock(
                        resultSet.getString("world_name"),
                        resultSet.getInt("x"),
                        resultSet.getInt("y"),
                        resultSet.getInt("z"),
                        resultSet.getString("prior_block_data")
                ));
            }
            return blocks;
        }
    }

    public void clearBarrierBlocks() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM gladiator_barrier_blocks")) {
            statement.executeUpdate();
        }
    }

    private void replaceFighters(GladiatorFight fight) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM gladiator_fighters WHERE fight_id = ?")) {
                    delete.setObject(1, fight.fightId());
                    delete.executeUpdate();
                }
                String insert = """
                        INSERT INTO gladiator_fighters (fight_id, account_id, team_id, slot_index, alive)
                        VALUES (?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement statement = connection.prepareStatement(insert)) {
                    for (GladiatorFighter fighter : fight.fighterList()) {
                        statement.setObject(1, fight.fightId());
                        statement.setObject(2, fighter.accountId());
                        statement.setObject(3, fighter.teamId());
                        statement.setInt(4, fighter.slotIndex());
                        statement.setBoolean(5, fighter.alive());
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

    private void hydrate(GladiatorSession session) throws SQLException {
        session.loadSchedule(loadSchedule(session.sessionId()));
        session.loadFights(loadFights(session.sessionId()));
        session.loadLineups(loadLineups(session.sessionId()));
        session.loadQuota(loadQuota(session.sessionId()));
        session.loadKitBans(loadKitBans(session.sessionId()));
        session.setDuelPending(loadDuelPending(session.sessionId()).orElse(null));
        session.loadBodySnapshots(loadBodySnapshots(session.sessionId()));
    }

    private List<GladiatorScheduledEntry> loadSchedule(UUID sessionId) throws SQLException {
        String sql = """
                SELECT bout_index, team_a_id, team_b_id, status
                FROM gladiator_schedule
                WHERE session_id = ?
                ORDER BY bout_index
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<GladiatorScheduledEntry> entries = new ArrayList<>();
                while (resultSet.next()) {
                    entries.add(new GladiatorScheduledEntry(
                            resultSet.getInt("bout_index"),
                            (UUID) resultSet.getObject("team_a_id"),
                            (UUID) resultSet.getObject("team_b_id"),
                            GladiatorScheduleStatus.valueOf(resultSet.getString("status"))
                    ));
                }
                return entries;
            }
        }
    }

    private List<GladiatorFight> loadFights(UUID sessionId) throws SQLException {
        String sql = """
                SELECT fight_id, fight_kind, bout_index, team_a_id, team_b_id, kit_id,
                       status, winning_team_id, started_at, ended_at
                FROM gladiator_fights
                WHERE session_id = ?
                ORDER BY started_at
                """;
        List<GladiatorFight> fights = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Integer boutIndex = resultSet.getObject("bout_index") == null
                            ? null
                            : resultSet.getInt("bout_index");
                    Instant endedAt = resultSet.getTimestamp("ended_at") == null
                            ? null
                            : resultSet.getTimestamp("ended_at").toInstant();
                    fights.add(new GladiatorFight(
                            (UUID) resultSet.getObject("fight_id"),
                            GladiatorFightKind.valueOf(resultSet.getString("fight_kind")),
                            boutIndex,
                            (UUID) resultSet.getObject("team_a_id"),
                            (UUID) resultSet.getObject("team_b_id"),
                            resultSet.getString("kit_id"),
                            GladiatorFightStatus.valueOf(resultSet.getString("status")),
                            (UUID) resultSet.getObject("winning_team_id"),
                            resultSet.getTimestamp("started_at").toInstant(),
                            endedAt
                    ));
                }
            }
        }
        // Load fighters after releasing the fights connection (pool size is small in tests).
        for (GladiatorFight fight : fights) {
            fight.loadFighters(loadFighters(fight.fightId()));
        }
        return fights;
    }

    private List<GladiatorFighter> loadFighters(UUID fightId) throws SQLException {
        String sql = """
                SELECT account_id, team_id, slot_index, alive
                FROM gladiator_fighters
                WHERE fight_id = ?
                ORDER BY slot_index
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, fightId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<GladiatorFighter> fighters = new ArrayList<>();
                while (resultSet.next()) {
                    fighters.add(new GladiatorFighter(
                            (UUID) resultSet.getObject("account_id"),
                            (UUID) resultSet.getObject("team_id"),
                            resultSet.getInt("slot_index"),
                            resultSet.getBoolean("alive")
                    ));
                }
                return fighters;
            }
        }
    }

    private Map<UUID, Map<Integer, UUID>> loadLineups(UUID sessionId) throws SQLException {
        String sql = """
                SELECT team_id, slot_index, account_id
                FROM gladiator_lineups
                WHERE session_id = ?
                ORDER BY team_id, slot_index
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<UUID, Map<Integer, UUID>> lineups = new LinkedHashMap<>();
                while (resultSet.next()) {
                    UUID teamId = (UUID) resultSet.getObject("team_id");
                    lineups.computeIfAbsent(teamId, ignored -> new LinkedHashMap<>())
                            .put(resultSet.getInt("slot_index"), (UUID) resultSet.getObject("account_id"));
                }
                return lineups;
            }
        }
    }

    private Map<UUID, Integer> loadQuota(UUID sessionId) throws SQLException {
        String sql = """
                SELECT account_id, fights_used
                FROM gladiator_quota
                WHERE session_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<UUID, Integer> quota = new LinkedHashMap<>();
                while (resultSet.next()) {
                    quota.put((UUID) resultSet.getObject("account_id"), resultSet.getInt("fights_used"));
                }
                return quota;
            }
        }
    }

    private List<String> loadKitBans(UUID sessionId) throws SQLException {
        String sql = """
                SELECT kit_id
                FROM gladiator_kit_bans
                WHERE session_id = ?
                ORDER BY ban_order
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> bans = new ArrayList<>();
                while (resultSet.next()) {
                    bans.add(resultSet.getString("kit_id"));
                }
                return bans;
            }
        }
    }

    private Optional<GladiatorDuelPending> loadDuelPending(UUID sessionId) throws SQLException {
        String sql = """
                SELECT fighter_a_account_id, fighter_b_account_id,
                       fighter_a_team_id, fighter_b_team_id, next_ban_account_id
                FROM gladiator_duel_pending
                WHERE session_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new GladiatorDuelPending(
                        (UUID) resultSet.getObject("fighter_a_account_id"),
                        (UUID) resultSet.getObject("fighter_b_account_id"),
                        (UUID) resultSet.getObject("fighter_a_team_id"),
                        (UUID) resultSet.getObject("fighter_b_team_id"),
                        (UUID) resultSet.getObject("next_ban_account_id")
                ));
            }
        }
    }

    private Map<UUID, GladiatorBodySnapshot> loadBodySnapshots(UUID sessionId) throws SQLException {
        String sql = """
                SELECT account_id, world_name, x, y, z, yaw, pitch,
                       inventory_data, armor_data, offhand_data, ender_chest_data,
                       health, food_level, saturation, exhaustion, level, total_experience, exp,
                       game_mode, potion_effects_data
                FROM gladiator_body_snapshots
                WHERE session_id = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<UUID, GladiatorBodySnapshot> snapshots = new LinkedHashMap<>();
                while (resultSet.next()) {
                    UUID accountId = (UUID) resultSet.getObject("account_id");
                    snapshots.put(accountId, new GladiatorBodySnapshot(
                            accountId,
                            resultSet.getString("world_name"),
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            resultSet.getFloat("yaw"),
                            resultSet.getFloat("pitch"),
                            resultSet.getBytes("inventory_data"),
                            resultSet.getBytes("armor_data"),
                            resultSet.getBytes("offhand_data"),
                            resultSet.getBytes("ender_chest_data"),
                            resultSet.getDouble("health"),
                            resultSet.getInt("food_level"),
                            resultSet.getFloat("saturation"),
                            resultSet.getFloat("exhaustion"),
                            resultSet.getInt("level"),
                            resultSet.getInt("total_experience"),
                            resultSet.getFloat("exp"),
                            resultSet.getString("game_mode"),
                            resultSet.getBytes("potion_effects_data")
                    ));
                }
                return snapshots;
            }
        }
    }

    private static GladiatorSession mapSession(ResultSet resultSet) throws SQLException {
        Instant endedAt = resultSet.getTimestamp("ended_at") == null
                ? null
                : resultSet.getTimestamp("ended_at").toInstant();
        return new GladiatorSession(
                (UUID) resultSet.getObject("session_id"),
                GladiatorPhase.valueOf(resultSet.getString("phase")),
                resultSet.getInt("schedule_index"),
                resultSet.getBoolean("payout_completed"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                endedAt
        );
    }

    private static void setUuidOrNull(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }
}
