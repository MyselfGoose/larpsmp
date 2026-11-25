package com.larpsmp.moneyevent.acehunt;

import com.larpsmp.moneyevent.playerstate.AccountPlayerState;
import com.larpsmp.moneyevent.playerstate.AccountPlayerStateRepository;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Paper implementation of Ace Hunt world side effects.
 */
public final class BukkitAceHuntWorldActions implements AceHuntWorldActions {

    private final Plugin plugin;
    private final AccountSessionManager sessions;
    private final AccountPlayerStateRepository states;
    private final Consumer<String> errorLogger;
    private final Logger logger;

    public BukkitAceHuntWorldActions(
            Plugin plugin,
            AccountSessionManager sessions,
            AccountPlayerStateRepository states,
            Consumer<String> errorLogger
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.states = Objects.requireNonNull(states, "states");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
        this.logger = plugin.getLogger();
    }

    @Override
    public void teleportToSpawn(UUID accountId, AceHuntSpawnPoint spawn) {
        if (spawn == null || !spawn.isDefined()) {
            return;
        }
        runForPlayer(accountId, player -> {
            World world = Bukkit.getWorld(spawn.world());
            if (world == null) {
                errorLogger.accept("Ace Hunt spawn world missing: " + spawn.world());
                return;
            }
            Location location = new Location(
                    world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
            player.teleport(location);
        });
    }

    @Override
    public void teleportToSnapshot(UUID accountId, AceHuntLocationSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        runForPlayer(accountId, player -> {
            World world = Bukkit.getWorld(snapshot.worldName());
            if (world == null) {
                errorLogger.accept("Ace Hunt restore world missing: " + snapshot.worldName());
                return;
            }
            player.teleport(new Location(
                    world,
                    snapshot.x(),
                    snapshot.y(),
                    snapshot.z(),
                    snapshot.yaw(),
                    snapshot.pitch()));
        });
    }

    @Override
    public void applyTerritoryBorder(UUID accountId, AceHuntTerritory territory) {
        if (territory == null || !territory.region().isDefined()) {
            return;
        }
        AceHuntCuboid region = territory.region();
        runForPlayer(accountId, player -> {
            World world = Bukkit.getWorld(region.world());
            if (world == null) {
                return;
            }
            WorldBorder border = Bukkit.createWorldBorder();
            border.setCenter(region.centerX(), region.centerZ());
            // Padding so the square covers the cuboid; +1 for block edges.
            border.setSize(region.borderDiameter() + 1.0);
            border.setDamageAmount(0.0);
            border.setWarningDistance(0);
            player.setWorldBorder(border);
        });
    }

    @Override
    public void clearBorder(UUID accountId) {
        runForPlayer(accountId, player -> player.setWorldBorder(null));
    }

    @Override
    public void setSpectator(UUID accountId) {
        runForPlayer(accountId, player -> player.setGameMode(GameMode.SPECTATOR));
        correctOfflineGameMode(accountId, GameMode.SPECTATOR.name());
    }

    @Override
    public void setSurvival(UUID accountId) {
        runForPlayer(accountId, player -> {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        });
        correctOfflineGameMode(accountId, GameMode.SURVIVAL.name());
    }

    @Override
    public void correctOfflineGameMode(UUID accountId, String gameMode) {
        try {
            Optional<AccountPlayerState> existing = states.findByAccountId(accountId);
            if (existing.isEmpty()) {
                return;
            }
            AccountPlayerState state = existing.get();
            if (gameMode.equals(state.gameMode())) {
                return;
            }
            AccountPlayerState updated = new AccountPlayerState(
                    state.accountId(),
                    state.schemaVersion(),
                    state.worldName(),
                    state.x(),
                    state.y(),
                    state.z(),
                    state.yaw(),
                    state.pitch(),
                    state.inventoryData(),
                    state.armorData(),
                    state.offhandData(),
                    state.enderChestData(),
                    state.health(),
                    state.foodLevel(),
                    state.saturation(),
                    state.exhaustion(),
                    state.level(),
                    state.totalExperience(),
                    state.exp(),
                    gameMode,
                    state.potionEffectsData(),
                    state.updatedAt()
            );
            states.upsert(updated);
        } catch (SQLException exception) {
            errorLogger.accept("Could not correct game mode for " + accountId + ": " + exception.getMessage());
        }
    }

    @Override
    public @Nullable AceHuntLocationSnapshot captureLocation(
            UUID accountId,
            AceHuntLocationSnapshot.ParticipantRole role
    ) {
        Optional<AccountSession> session = sessions.findByAccountId(accountId);
        if (session.isEmpty()) {
            return null;
        }
        Player player = Bukkit.getPlayer(session.get().minecraftUuid());
        if (player == null || player.getWorld() == null) {
            return null;
        }
        Location location = player.getLocation();
        return new AceHuntLocationSnapshot(
                accountId,
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                role
        );
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
    public void damage(UUID accountId, double amount) {
        runForPlayer(accountId, player -> {
            if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
                return;
            }
            player.damage(amount);
        });
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
}
