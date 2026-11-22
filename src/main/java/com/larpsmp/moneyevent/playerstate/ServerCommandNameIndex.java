package com.larpsmp.moneyevent.playerstate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps Paper's {@code PlayerList#playersByName} map aligned with the LarpSMP account username.
 *
 * <p>Vanilla / Brigadier player arguments ({@code /gamemode}, {@code /tp}, {@code /give}, …) resolve
 * through that map. {@link Player#setPlayerProfile} updates the GameProfile shown to clients but does
 * <em>not</em> re-key the map, so commands keep resolving the launcher nickname.
 *
 * <p>Access path (Paper 1.21+): {@code CraftPlayer#getHandle()} for the {@code ServerPlayer} value and
 * {@code CraftServer#getHandle()} for the live {@code PlayerList} / {@code playersByName} map.
 * Keys are lower-cased with {@link Locale#ROOT}, matching Mojang's {@code getPlayerByName}.
 */
public final class ServerCommandNameIndex {

    private final Logger logger;
    private final AtomicReference<@Nullable Access> access = new AtomicReference<>();

    public ServerCommandNameIndex(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Discovers reflective accessors eagerly so boot logs show readiness before the first join.
     *
     * @return true when the Paper {@code playersByName} map is reachable
     */
    public boolean warmUp() {
        return access() != null;
    }

    /**
     * Re-keys the online-player name index so {@code accountUsername} resolves to {@code player}.
     *
     * @param previousName launcher / prior indexed name (may be null)
     * @return true when {@link Bukkit#getPlayerExact(String)} resolves to this player afterward
     */
    public boolean rebind(Player player, @Nullable String previousName, String accountUsername) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(accountUsername, "accountUsername");
        if (accountUsername.isBlank()) {
            return false;
        }

        String targetKey = normalize(accountUsername);
        String previousKey = previousName == null || previousName.isBlank() ? null : normalize(previousName);
        if (previousKey != null && previousKey.equals(targetKey)) {
            return resolvesTo(player, accountUsername);
        }

        Player colliding = Bukkit.getPlayerExact(accountUsername);
        if (colliding != null && !colliding.getUniqueId().equals(player.getUniqueId())) {
            logger.warning("Cannot rebind command name '" + accountUsername + "' for "
                    + player.getUniqueId() + ": already used by " + colliding.getUniqueId());
            return false;
        }

        Access reflective = access();
        if (reflective == null) {
            logger.severe("Command name index unavailable; vanilla commands will keep using launcher nicknames.");
            return resolvesTo(player, accountUsername);
        }

        try {
            Object handle = reflective.serverPlayer(player);
            Map<String, Object> byName = reflective.playersByName();
            synchronized (byName) {
                rekey(byName, handle, previousKey, targetKey);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.log(Level.SEVERE, "Failed to rebind command name index to '" + accountUsername + "'",
                    exception);
            access.set(null);
            return false;
        }

        boolean ok = resolvesTo(player, accountUsername);
        if (!ok) {
            logger.warning("Command name rebind did not make getPlayerExact('" + accountUsername
                    + "') resolve to " + player.getUniqueId());
        }
        return ok;
    }

    public boolean isAvailable() {
        return access() != null;
    }

    /**
     * Mutates {@code playersByName} so {@code handle} is indexed only under {@code targetKey}.
     * Package-visible for unit tests.
     */
    static void rekey(
            Map<String, Object> byName,
            Object handle,
            @Nullable String previousKey,
            String targetKey
    ) {
        Objects.requireNonNull(byName, "byName");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(targetKey, "targetKey");
        if (previousKey != null) {
            Object mapped = byName.get(previousKey);
            if (mapped == null || mapped == handle) {
                byName.remove(previousKey);
            }
        }
        byName.entrySet().removeIf(entry ->
                entry.getValue() == handle && !targetKey.equals(entry.getKey()));
        byName.put(targetKey, handle);
    }

    static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean resolvesTo(Player player, String accountUsername) {
        Player resolved = Bukkit.getPlayerExact(accountUsername);
        return resolved != null && resolved.getUniqueId().equals(player.getUniqueId());
    }

    private @Nullable Access access() {
        Access cached = access.get();
        if (cached != null) {
            return cached;
        }
        Optional<Access> discovered = Access.discover(logger);
        discovered.ifPresent(value -> access.compareAndSet(null, value));
        return access.get();
    }

    /** Visible for tests. */
    void installAccessForTests(Access reflective) {
        access.set(reflective);
    }

    /**
     * CraftPlayer.getHandle() + CraftServer.getHandle().playersByName.
     */
    static final class Access {
        private final Method craftPlayerGetHandle;
        private final Method craftServerGetHandle;
        private final Field playersByNameField;

        Access(Method craftPlayerGetHandle, Method craftServerGetHandle, Field playersByNameField) {
            this.craftPlayerGetHandle = craftPlayerGetHandle;
            this.craftServerGetHandle = craftServerGetHandle;
            this.playersByNameField = playersByNameField;
        }

        Object serverPlayer(Player player) throws ReflectiveOperationException {
            Object handle = craftPlayerGetHandle.invoke(player);
            if (handle == null) {
                throw new IllegalStateException("CraftPlayer.getHandle() returned null");
            }
            return handle;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> playersByName() throws ReflectiveOperationException {
            Object playerList = craftServerGetHandle.invoke(Bukkit.getServer());
            if (playerList == null) {
                throw new IllegalStateException("CraftServer.getHandle() returned null");
            }
            Object map = playersByNameField.get(playerList);
            if (!(map instanceof Map<?, ?>)) {
                throw new IllegalStateException("playersByName is not a Map");
            }
            return (Map<String, Object>) map;
        }

        static Optional<Access> discover(Logger logger) {
            try {
                Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
                Method playerGetHandle = resolveServerPlayerHandle(craftPlayer);
                if (playerGetHandle == null) {
                    logger.severe("Could not resolve CraftPlayer.getHandle() → ServerPlayer.");
                    return Optional.empty();
                }

                Class<?> craftServer = Class.forName("org.bukkit.craftbukkit.CraftServer");
                Method serverGetHandle = craftServer.getMethod("getHandle");
                Class<?> playerListType = serverGetHandle.getReturnType();

                Field playersByName = findPlayersByNameField(playerListType);
                if (playersByName == null) {
                    logger.severe("Could not resolve PlayerList.playersByName for command name rebind.");
                    return Optional.empty();
                }
                playersByName.setAccessible(true);

                logger.info("Account command-name index ready ("
                        + playerListType.getSimpleName()
                        + " / " + playersByName.getDeclaringClass().getSimpleName()
                        + "#" + playersByName.getName() + ").");
                return Optional.of(new Access(playerGetHandle, serverGetHandle, playersByName));
            } catch (ClassNotFoundException exception) {
                logger.log(Level.SEVERE, "CraftBukkit classes missing; command-name rebind disabled.", exception);
                return Optional.empty();
            } catch (ReflectiveOperationException exception) {
                logger.log(Level.SEVERE, "Failed to discover command-name index accessors.", exception);
                return Optional.empty();
            }
        }

        /**
         * Prefer the covariant {@code ServerPlayer getHandle()} over bridge methods returning
         * {@code Entity}/{@code LivingEntity}/{@code Player}.
         */
        private static @Nullable Method resolveServerPlayerHandle(Class<?> craftPlayer) {
            Method fallback = null;
            for (Method method : craftPlayer.getMethods()) {
                if (!"getHandle".equals(method.getName()) || method.getParameterCount() != 0) {
                    continue;
                }
                String returnName = method.getReturnType().getSimpleName();
                if ("ServerPlayer".equals(returnName)) {
                    return method;
                }
                if (fallback == null) {
                    fallback = method;
                }
            }
            return fallback;
        }

        private static @Nullable Field findPlayersByNameField(@Nullable Class<?> type) {
            Class<?> cursor = type;
            while (cursor != null && cursor != Object.class) {
                try {
                    Field field = cursor.getDeclaredField("playersByName");
                    if (Map.class.isAssignableFrom(field.getType())) {
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {
                    // continue hierarchy
                }
                for (Field field : cursor.getDeclaredFields()) {
                    if (!Map.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    String generic = field.getGenericType().getTypeName().toLowerCase(Locale.ROOT);
                    if (generic.contains("string") && generic.contains("serverplayer")) {
                        return field;
                    }
                }
                cursor = cursor.getSuperclass();
            }
            return null;
        }
    }
}
