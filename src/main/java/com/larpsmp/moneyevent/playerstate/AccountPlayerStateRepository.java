package com.larpsmp.moneyevent.playerstate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * JDBC persistence for {@link AccountPlayerState}.
 */
public final class AccountPlayerStateRepository {

    private static final String COLUMNS = """
            account_id, schema_version, world_name, x, y, z, yaw, pitch,
            inventory_data, armor_data, offhand_data, ender_chest_data,
            health, food_level, saturation, exhaustion,
            level, total_experience, exp, game_mode, potion_effects_data, updated_at
            """;

    private final DataSource dataSource;

    public AccountPlayerStateRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<AccountPlayerState> findByAccountId(UUID accountId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM account_player_states WHERE account_id = ?";
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

    public void upsert(AccountPlayerState state) throws SQLException {
        String sql = """
                INSERT INTO account_player_states (
                    account_id, schema_version, world_name, x, y, z, yaw, pitch,
                    inventory_data, armor_data, offhand_data, ender_chest_data,
                    health, food_level, saturation, exhaustion,
                    level, total_experience, exp, game_mode, potion_effects_data, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?
                )
                ON CONFLICT (account_id) DO UPDATE SET
                    schema_version = EXCLUDED.schema_version,
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
                    potion_effects_data = EXCLUDED.potion_effects_data,
                    updated_at = EXCLUDED.updated_at
                """;
        Instant updatedAt = state.updatedAt() == null ? Instant.now() : state.updatedAt();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setObject(index++, state.accountId());
            statement.setInt(index++, state.schemaVersion());
            statement.setString(index++, state.worldName());
            statement.setDouble(index++, state.x());
            statement.setDouble(index++, state.y());
            statement.setDouble(index++, state.z());
            statement.setFloat(index++, state.yaw());
            statement.setFloat(index++, state.pitch());
            statement.setBytes(index++, state.inventoryData());
            statement.setBytes(index++, state.armorData());
            statement.setBytes(index++, state.offhandData());
            statement.setBytes(index++, state.enderChestData());
            statement.setDouble(index++, state.health());
            statement.setInt(index++, state.foodLevel());
            statement.setFloat(index++, state.saturation());
            statement.setFloat(index++, state.exhaustion());
            statement.setInt(index++, state.level());
            statement.setInt(index++, state.totalExperience());
            statement.setFloat(index++, state.exp());
            statement.setString(index++, state.gameMode());
            statement.setBytes(index++, state.potionEffectsData());
            statement.setTimestamp(index, Timestamp.from(updatedAt));
            statement.executeUpdate();
        }
    }

    private static AccountPlayerState mapRow(ResultSet resultSet) throws SQLException {
        Timestamp updated = resultSet.getTimestamp("updated_at");
        return new AccountPlayerState(
                (UUID) resultSet.getObject("account_id"),
                resultSet.getInt("schema_version"),
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
                resultSet.getBytes("potion_effects_data"),
                updated == null ? null : updated.toInstant()
        );
    }
}
