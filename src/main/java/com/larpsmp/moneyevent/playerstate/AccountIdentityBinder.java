package com.larpsmp.moneyevent.playerstate;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import java.util.Objects;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Makes the LarpSMP account username the universal in-server identity.
 *
 * <ol>
 *   <li>During configuration-phase auth, rename the connecting {@link PlayerProfile} so the player
 *       enters {@code PlayerList} under the account username.</li>
 *   <li>On join, apply display/tab identity and {@link Player#setPlayerProfile}.</li>
 *   <li>Re-key Paper's command name index so vanilla Brigadier targets ({@code /gamemode},
 *       {@code /tp}, …) resolve the account username — not the launcher nickname.</li>
 * </ol>
 */
public final class AccountIdentityBinder {

    private final ServerCommandNameIndex commandNames;
    private final Logger logger;

    public AccountIdentityBinder(Logger logger) {
        this(new ServerCommandNameIndex(logger), logger);
    }

    public AccountIdentityBinder(ServerCommandNameIndex commandNames, Logger logger) {
        this.commandNames = Objects.requireNonNull(commandNames, "commandNames");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public ServerCommandNameIndex commandNames() {
        return commandNames;
    }

    /**
     * Renames the configuration-phase profile before world join.
     *
     * @return previous launcher profile name (may be null)
     */
    public @Nullable String bindConfigurationProfile(
            PlayerConfigurationConnection connection,
            String accountUsername
    ) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(accountUsername, "accountUsername");
        PlayerProfile profile = connection.getProfile();
        String previous = profile.getName();
        if (accountUsername.equals(previous)) {
            return previous;
        }
        // Paper marks setName deprecated/for-removal, but configuration connections expose no
        // setPlayerProfile. Renaming here is required so PlayerList indexes the account username
        // at placeNewPlayer. Online bind still uses createProfileExact + command-name reindex.
        @SuppressWarnings("removal")
        String replaced = profile.setName(accountUsername);
        logger.info("Bound configuration profile name '"
                + (replaced == null ? previous : replaced)
                + "' → '" + accountUsername + "'");
        return replaced != null ? replaced : previous;
    }

    /**
     * Full online bind: GameProfile, display/tab names, and vanilla command name index.
     */
    public BindResult bindOnlinePlayer(Player player, String accountUsername, @Nullable String launcherName) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(accountUsername, "accountUsername");

        String beforeProfileName = player.getName();
        String effectivePrevious = firstNonBlank(launcherName, beforeProfileName);

        Player colliding = Bukkit.getPlayerExact(accountUsername);
        if (colliding != null && !colliding.getUniqueId().equals(player.getUniqueId())) {
            logger.warning("Account identity collision: '" + accountUsername + "' already online as "
                    + colliding.getUniqueId());
            return BindResult.collision(accountUsername);
        }

        applyGameProfile(player, accountUsername);
        applyDisplayNames(player, accountUsername);

        boolean indexed = commandNames.rebind(player, effectivePrevious, accountUsername);
        // If the profile rename already indexed the account name, also clear any leftover launcher key.
        if (!indexed && launcherName != null && !launcherName.isBlank()
                && !launcherName.equalsIgnoreCase(accountUsername)) {
            indexed = commandNames.rebind(player, launcherName, accountUsername);
        }
        if (!indexed) {
            indexed = commandNames.rebind(player, beforeProfileName, accountUsername);
        }

        boolean exact = matchesExact(player, accountUsername);
        if (exact) {
            logger.info("Account identity online for '" + accountUsername + "' (launcher='"
                    + nullToEmpty(launcherName) + "', uuid=" + player.getUniqueId() + ")");
            return BindResult.ok(accountUsername, launcherName, true);
        }

        logger.warning("Account identity display applied for '" + accountUsername
                + "' but vanilla getPlayerExact still misses; commands may require launcher name '"
                + nullToEmpty(effectivePrevious) + "'");
        return BindResult.partial(accountUsername, launcherName);
    }

    public static Component joinMessage(String accountUsername) {
        return Component.text(accountUsername + " joined the game", NamedTextColor.YELLOW);
    }

    public static Component quitMessage(String accountUsername) {
        return Component.text(accountUsername + " left the game", NamedTextColor.YELLOW);
    }

    private static void applyGameProfile(Player player, String accountUsername) {
        PlayerProfile current = player.getPlayerProfile();
        if (accountUsername.equals(current.getName())) {
            return;
        }
        PlayerProfile renamed = Bukkit.createProfileExact(player.getUniqueId(), accountUsername);
        renamed.setProperties(current.getProperties());
        player.setPlayerProfile(renamed);
    }

    private static void applyDisplayNames(Player player, String accountUsername) {
        Component name = Component.text(accountUsername, NamedTextColor.WHITE);
        player.displayName(name);
        player.playerListName(name);
        player.setDisplayName(accountUsername);
        player.setPlayerListName(accountUsername);
    }

    private static boolean matchesExact(Player player, String accountUsername) {
        Player resolved = Bukkit.getPlayerExact(accountUsername);
        return resolved != null && resolved.getUniqueId().equals(player.getUniqueId());
    }

    private static @Nullable String firstNonBlank(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    public record BindResult(
            Status status,
            String accountUsername,
            @Nullable String launcherName,
            boolean commandIndexBound
    ) {
        public enum Status {
            OK,
            PARTIAL,
            COLLISION
        }

        static BindResult ok(String accountUsername, @Nullable String launcherName, boolean indexed) {
            return new BindResult(Status.OK, accountUsername, launcherName, indexed);
        }

        static BindResult partial(String accountUsername, @Nullable String launcherName) {
            return new BindResult(Status.PARTIAL, accountUsername, launcherName, false);
        }

        static BindResult collision(String accountUsername) {
            return new BindResult(Status.COLLISION, accountUsername, null, false);
        }

        public boolean successful() {
            return status == Status.OK;
        }
    }
}
