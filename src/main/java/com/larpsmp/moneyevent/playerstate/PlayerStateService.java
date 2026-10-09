package com.larpsmp.moneyevent.playerstate;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

/**
 * Loads, applies, captures, and persists account-keyed player bodies.
 */
public final class PlayerStateService {

    private final AccountPlayerStateRepository repository;
    private final PlayerStateConfig config;
    private final Logger logger;

    public PlayerStateService(
            AccountPlayerStateRepository repository,
            PlayerStateConfig config,
            Logger logger
    ) {
        this.repository = repository;
        this.config = config;
        this.logger = logger;
    }

    public PlayerStateConfig config() {
        return config;
    }

    /**
     * Loads saved state or builds defaults for a first-time account.
     */
    public AccountPlayerState preload(UUID accountId) throws SQLException, IOException {
        Optional<AccountPlayerState> existing = repository.findByAccountId(accountId);
        if (existing.isPresent()) {
            return existing.get();
        }
        return createDefaultState(accountId);
    }

    /**
     * Builds a default body using config coordinates only (safe off the main thread).
     * World name may be a placeholder resolved later on the main thread via {@link #resolveLocation}.
     */
    public AccountPlayerState createDefaultState(UUID accountId) throws IOException {
        PlayerStateConfig.DefaultSpawn defaults = config.defaultSpawn();
        String worldName = defaults.hasWorld() ? defaults.world() : "world";
        return new AccountPlayerState(
                accountId,
                PlayerStateCodec.CURRENT_SCHEMA_VERSION,
                worldName,
                defaults.x(),
                defaults.y(),
                defaults.z(),
                defaults.yaw(),
                defaults.pitch(),
                PlayerStateCodec.emptyItemArray(36),
                PlayerStateCodec.emptyItemArray(4),
                PlayerStateCodec.emptyItemArray(1),
                PlayerStateCodec.emptyItemArray(27),
                20.0,
                20,
                5.0f,
                0.0f,
                0,
                0,
                0.0f,
                GameMode.SURVIVAL.name(),
                PlayerStateCodec.emptyEffects(),
                null
        );
    }

    /**
     * Resolves the configured default spawn. Must run on the main thread.
     */
    public Location resolveDefaultSpawn() {
        PlayerStateConfig.DefaultSpawn defaults = config.defaultSpawn();
        World world = null;
        if (defaults.hasWorld()) {
            world = Bukkit.getWorld(defaults.world());
        }
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().getFirst();
        }
        if (world == null) {
            return new Location(null, defaults.x(), defaults.y(), defaults.z(), defaults.yaw(), defaults.pitch());
        }
        if (!defaults.hasWorld()) {
            Location worldSpawn = world.getSpawnLocation();
            return new Location(
                    world,
                    worldSpawn.getX() + 0.5,
                    worldSpawn.getY(),
                    worldSpawn.getZ() + 0.5,
                    defaults.yaw(),
                    defaults.pitch()
            );
        }
        return new Location(world, defaults.x(), defaults.y(), defaults.z(), defaults.yaw(), defaults.pitch());
    }

    /**
     * Resolves a saved or default location. Must run on the main thread.
     * For first-time defaults with placeholder world {@code world}, falls back to the
     * server default-world spawn when the named world is missing or when coords are config placeholders.
     */
    public @Nullable Location resolveLocation(AccountPlayerState state) {
        World world = Bukkit.getWorld(state.worldName());
        PlayerStateConfig.DefaultSpawn defaults = config.defaultSpawn();
        boolean firstJoinPlaceholder = !defaults.hasWorld()
                && "world".equals(state.worldName())
                && state.updatedAt() == null;
        if (world == null || firstJoinPlaceholder) {
            if (world == null) {
                logger.warning("Saved world '" + state.worldName() + "' missing for account "
                        + state.accountId() + "; using default spawn.");
            }
            return resolveDefaultSpawn();
        }
        return new Location(world, state.x(), state.y(), state.z(), state.yaw(), state.pitch());
    }

    public void apply(Player player, AccountPlayerState state) throws IOException {
        Location location = resolveLocation(state);
        if (location.getWorld() != null) {
            player.teleport(location);
        }

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        player.getEnderChest().clear();

        ItemStack[] contents = PlayerStateCodec.decodeItemArray(state.inventoryData());
        ItemStack[] armor = PlayerStateCodec.decodeItemArray(state.armorData());
        ItemStack offhand = PlayerStateCodec.decodeSingleItem(state.offhandData());
        ItemStack[] ender = PlayerStateCodec.decodeItemArray(state.enderChestData());

        inventory.setContents(pad(contents, 36));
        inventory.setArmorContents(pad(armor, 4));
        inventory.setItemInOffHand(offhand == null ? ItemStack.empty() : offhand);
        player.getEnderChest().setContents(pad(ender, 27));

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(clamp(state.health(), 0.0, max));
        player.setFoodLevel(clampInt(state.foodLevel(), 0, 20));
        player.setSaturation(Math.max(0.0f, state.saturation()));
        player.setExhaustion(Math.max(0.0f, state.exhaustion()));

        player.setLevel(Math.max(0, state.level()));
        player.setTotalExperience(Math.max(0, state.totalExperience()));
        player.setExp(clamp(state.exp(), 0.0f, 1.0f));

        try {
            player.setGameMode(GameMode.valueOf(state.gameMode()));
        } catch (IllegalArgumentException exception) {
            logger.warning("Invalid saved game mode '" + state.gameMode() + "' for account "
                    + state.accountId() + "; defaulting to SURVIVAL.");
            player.setGameMode(GameMode.SURVIVAL);
        }

        for (PotionEffect active : player.getActivePotionEffects()) {
            player.removePotionEffect(active.getType());
        }
        List<PotionEffect> effects = PlayerStateCodec.decodePotionEffects(state.potionEffectsData());
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
    }

    public AccountPlayerState capture(Player player, UUID accountId) throws IOException {
        Location location = player.getLocation();
        String worldName = location.getWorld() == null
                ? config.defaultSpawn().world() == null ? "world" : config.defaultSpawn().world()
                : location.getWorld().getName();

        PlayerInventory inventory = player.getInventory();
        return new AccountPlayerState(
                accountId,
                PlayerStateCodec.CURRENT_SCHEMA_VERSION,
                worldName,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                PlayerStateCodec.encodeItemArray(inventory.getContents()),
                PlayerStateCodec.encodeItemArray(inventory.getArmorContents()),
                PlayerStateCodec.encodeSingleItem(inventory.getItemInOffHand()),
                PlayerStateCodec.encodeItemArray(player.getEnderChest().getContents()),
                player.getHealth(),
                player.getFoodLevel(),
                player.getSaturation(),
                player.getExhaustion(),
                player.getLevel(),
                player.getTotalExperience(),
                player.getExp(),
                player.getGameMode().name(),
                PlayerStateCodec.encodePotionEffects(player.getActivePotionEffects()),
                Instant.now()
        );
    }

    public void save(AccountPlayerState state) throws SQLException {
        repository.upsert(state);
    }

    /**
     * Clears inventory-like data so vanilla UUID playerdata cannot leak account items.
     * Call only after a successful save.
     */
    public void clearVanillaShell(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(ItemStack.empty());
        player.getEnderChest().clear();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.setLevel(0);
        player.setTotalExperience(0);
        player.setExp(0.0f);
        player.setFoodLevel(20);
        player.setSaturation(0.0f);
        player.setExhaustion(0.0f);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(max);
    }

    public boolean savePlayer(Player player, UUID accountId) {
        try {
            AccountPlayerState state = capture(player, accountId);
            save(state);
            return true;
        } catch (IOException | SQLException exception) {
            logger.log(Level.SEVERE, "Failed to save player state for account " + accountId, exception);
            return false;
        }
    }

    public void saveAllOnline(Collection<Player> players, AccountSessionManager sessions) {
        for (Player player : players) {
            sessions.findByMinecraftUuid(player.getUniqueId()).ifPresent(session -> {
                if (savePlayer(player, session.accountId())) {
                    clearVanillaShell(player);
                }
            });
        }
    }

    private static ItemStack[] pad(ItemStack[] source, int size) {
        ItemStack[] result = new ItemStack[size];
        if (source == null) {
            return result;
        }
        int copy = Math.min(size, source.length);
        System.arraycopy(source, 0, result, 0, copy);
        return result;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
