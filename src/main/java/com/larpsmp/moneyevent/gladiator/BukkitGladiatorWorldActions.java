package com.larpsmp.moneyevent.gladiator;

import com.larpsmp.moneyevent.playerstate.AccountPlayerState;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.playerstate.PlayerStateCodec;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Paper implementation of Gladiators world side effects.
 */
public final class BukkitGladiatorWorldActions implements GladiatorWorldActions {

    private final Plugin plugin;
    private final AccountSessionManager sessions;
    private final TeamService teams;
    private final Consumer<String> errorLogger;

    public BukkitGladiatorWorldActions(
            Plugin plugin,
            AccountSessionManager sessions,
            TeamService teams,
            Consumer<String> errorLogger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    @Override
    public void teleport(UUID accountId, GladiatorSpawnPoint spawn) {
        if (spawn == null || !spawn.isDefined()) {
            return;
        }
        runForPlayer(accountId, player -> {
            World world = Bukkit.getWorld(spawn.world());
            if (world == null) {
                errorLogger.accept("Gladiators spawn world missing: " + spawn.world());
                return;
            }
            player.teleport(new Location(
                    world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch()));
        });
    }

    @Override
    public void applyKit(UUID accountId, GladiatorKit kit) {
        runForPlayer(accountId, player -> {
            try {
                PlayerInventory inventory = player.getInventory();
                inventory.clear();
                inventory.setContents(pad(PlayerStateCodec.decodeItemArray(kit.inventoryData()), 36));
                inventory.setArmorContents(pad(PlayerStateCodec.decodeItemArray(kit.armorData()), 4));
                ItemStack offhand = PlayerStateCodec.decodeSingleItem(kit.offhandData());
                inventory.setItemInOffHand(offhand == null ? ItemStack.empty() : offhand);
            } catch (IOException exception) {
                errorLogger.accept("Failed to apply kit: " + exception.getMessage());
            }
        });
    }

    @Override
    public void clearInventory(UUID accountId) {
        runForPlayer(accountId, player -> {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(ItemStack.empty());
        });
    }

    @Override
    public void healFully(UUID accountId) {
        runForPlayer(accountId, player -> {
            AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHealth == null ? 20.0 : maxHealth.getValue();
            player.setHealth(max);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setExhaustion(0.0f);
            player.setFireTicks(0);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        });
    }

    @Override
    public void setAdventure(UUID accountId) {
        runForPlayer(accountId, player -> player.setGameMode(GameMode.ADVENTURE));
    }

    @Override
    public void setSurvival(UUID accountId) {
        runForPlayer(accountId, player -> {
            if (player.getGameMode() != GameMode.SURVIVAL) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        });
    }

    @Override
    public Optional<GladiatorBodySnapshot> captureBody(UUID accountId) {
        Optional<AccountSession> session = sessions.findByAccountId(accountId);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        Player player = Bukkit.getPlayer(session.get().minecraftUuid());
        if (player == null || !player.isOnline() || player.getWorld() == null) {
            return Optional.empty();
        }
        try {
            Location location = player.getLocation();
            PlayerInventory inventory = player.getInventory();
            return Optional.of(new GladiatorBodySnapshot(
                    accountId,
                    location.getWorld().getName(),
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
                    PlayerStateCodec.encodePotionEffects(player.getActivePotionEffects())
            ));
        } catch (IOException exception) {
            errorLogger.accept("Failed to capture body: " + exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void restoreBody(GladiatorBodySnapshot snapshot) {
        runForPlayer(snapshot.accountId(), player -> {
            try {
                AccountPlayerState state = toAccountState(snapshot);
                applyState(player, state);
            } catch (IOException exception) {
                errorLogger.accept("Failed to restore body: " + exception.getMessage());
            }
        });
    }

    @Override
    public Optional<GladiatorKit> captureKitFromPlayer(UUID accountId, String kitId) {
        Optional<AccountSession> session = sessions.findByAccountId(accountId);
        if (session.isEmpty()) {
            return Optional.empty();
        }
        Player player = Bukkit.getPlayer(session.get().minecraftUuid());
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        try {
            PlayerInventory inventory = player.getInventory();
            return Optional.of(new GladiatorKit(
                    kitId,
                    PlayerStateCodec.encodeItemArray(inventory.getContents()),
                    PlayerStateCodec.encodeItemArray(inventory.getArmorContents()),
                    PlayerStateCodec.encodeSingleItem(inventory.getItemInOffHand()),
                    Instant.now()
            ));
        } catch (IOException exception) {
            errorLogger.accept("Failed to capture kit: " + exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<GladiatorBarrierBlock> fillBarriers(GladiatorCuboid region) {
        if (!region.isDefined()) {
            return List.of();
        }
        World world = Bukkit.getWorld(region.world());
        if (world == null) {
            errorLogger.accept("Barrier world missing: " + region.world());
            return List.of();
        }
        List<GladiatorBarrierBlock> prior = new ArrayList<>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    prior.add(new GladiatorBarrierBlock(
                            world.getName(), x, y, z, block.getBlockData().getAsString()));
                    block.setType(Material.BARRIER, false);
                }
            }
        }
        return prior;
    }

    @Override
    public int clearBarriers(List<GladiatorBarrierBlock> blocks) {
        int restored = 0;
        for (GladiatorBarrierBlock block : blocks) {
            World world = Bukkit.getWorld(block.worldName());
            if (world == null) {
                continue;
            }
            try {
                BlockData data = Bukkit.createBlockData(block.priorBlockData());
                world.getBlockAt(block.x(), block.y(), block.z()).setBlockData(data, false);
                restored++;
            } catch (IllegalArgumentException exception) {
                errorLogger.accept("Invalid barrier undo data at "
                        + block.worldName() + " " + block.x() + "," + block.y() + "," + block.z());
            }
        }
        return restored;
    }

    @Override
    public boolean isOnline(UUID accountId) {
        Optional<AccountSession> session = sessions.findByAccountId(accountId);
        if (session.isEmpty()) {
            return false;
        }
        Player player = Bukkit.getPlayer(session.get().minecraftUuid());
        return player != null && player.isOnline();
    }

    @Override
    public void broadcast(String message) {
        Bukkit.getServer().broadcast(net.kyori.adventure.text.Component.text(message));
    }

    @Override
    public void send(UUID accountId, String message) {
        runForPlayer(accountId, player -> player.sendMessage(message));
    }

    @Override
    public @Nullable String teamDisplayName(UUID teamId) {
        return teams.getTeam(teamId).map(Team::displayName).orElse(null);
    }

    @Override
    public @Nullable String username(UUID accountId) {
        return sessions.findByAccountId(accountId).map(AccountSession::username).orElse(null);
    }

    public static AccountPlayerState toAccountState(GladiatorBodySnapshot snapshot) {
        return new AccountPlayerState(
                snapshot.accountId(),
                PlayerStateCodec.CURRENT_SCHEMA_VERSION,
                snapshot.worldName(),
                snapshot.x(),
                snapshot.y(),
                snapshot.z(),
                snapshot.yaw(),
                snapshot.pitch(),
                snapshot.inventoryData(),
                snapshot.armorData(),
                snapshot.offhandData(),
                snapshot.enderChestData(),
                snapshot.health(),
                snapshot.foodLevel(),
                snapshot.saturation(),
                snapshot.exhaustion(),
                snapshot.level(),
                snapshot.totalExperience(),
                snapshot.exp(),
                snapshot.gameMode(),
                snapshot.potionEffectsData(),
                Instant.now()
        );
    }

    private void applyState(Player player, AccountPlayerState state) throws IOException {
        World world = Bukkit.getWorld(state.worldName());
        if (world != null) {
            player.teleport(new Location(
                    world, state.x(), state.y(), state.z(), state.yaw(), state.pitch()));
        }
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        player.getEnderChest().clear();
        inventory.setContents(pad(PlayerStateCodec.decodeItemArray(state.inventoryData()), 36));
        inventory.setArmorContents(pad(PlayerStateCodec.decodeItemArray(state.armorData()), 4));
        ItemStack offhand = PlayerStateCodec.decodeSingleItem(state.offhandData());
        inventory.setItemInOffHand(offhand == null ? ItemStack.empty() : offhand);
        player.getEnderChest().setContents(pad(PlayerStateCodec.decodeItemArray(state.enderChestData()), 27));

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth == null ? 20.0 : maxHealth.getValue();
        player.setHealth(Math.max(0.0, Math.min(max, state.health())));
        player.setFoodLevel(Math.max(0, Math.min(20, state.foodLevel())));
        player.setSaturation(Math.max(0.0f, state.saturation()));
        player.setExhaustion(Math.max(0.0f, state.exhaustion()));
        player.setLevel(Math.max(0, state.level()));
        player.setTotalExperience(Math.max(0, state.totalExperience()));
        player.setExp(Math.max(0.0f, Math.min(1.0f, state.exp())));
        try {
            player.setGameMode(GameMode.valueOf(state.gameMode()));
        } catch (IllegalArgumentException exception) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        for (var effect : PlayerStateCodec.decodePotionEffects(state.potionEffectsData())) {
            player.addPotionEffect(effect);
        }
    }

    private void runForPlayer(UUID accountId, Consumer<Player> action) {
        Optional<AccountSession> session = sessions.findByAccountId(accountId);
        if (session.isEmpty()) {
            return;
        }
        UUID minecraftUuid = session.get().minecraftUuid();
        Runnable task = () -> {
            Player player = Bukkit.getPlayer(minecraftUuid);
            if (player != null && player.isOnline()) {
                action.accept(player);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static ItemStack[] pad(ItemStack[] source, int size) {
        ItemStack[] result = new ItemStack[size];
        if (source == null) {
            return result;
        }
        System.arraycopy(source, 0, result, 0, Math.min(size, source.length));
        return result;
    }
}
