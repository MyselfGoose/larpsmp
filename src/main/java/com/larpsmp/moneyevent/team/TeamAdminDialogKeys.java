package com.larpsmp.moneyevent.team;

import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

/**
 * Stable custom-click identifiers for the Teams Admin dialog console.
 */
public final class TeamAdminDialogKeys {

    public static final String NAMESPACE = "larpsmp";

    public static final Key OPEN_HUB = Key.key(NAMESPACE, "team/hub");
    public static final Key OPEN_CREATE = Key.key(NAMESPACE, "team/open-create");
    public static final Key CREATE_SUBMIT = Key.key(NAMESPACE, "team/create");
    public static final Key OPEN_WARNINGS = Key.key(NAMESPACE, "team/warnings");
    public static final Key OPEN_TIERS = Key.key(NAMESPACE, "team/open-tiers");
    public static final Key OPEN_EDIT = Key.key(NAMESPACE, "team/open-edit");
    public static final Key EDIT_SUBMIT = Key.key(NAMESPACE, "team/edit");
    public static final Key OPEN_ASSIGN = Key.key(NAMESPACE, "team/open-assign");
    public static final Key ASSIGN_SUBMIT = Key.key(NAMESPACE, "team/assign");
    public static final Key OPEN_SET_CAPTAIN = Key.key(NAMESPACE, "team/open-set-captain");
    public static final Key SET_CAPTAIN_SUBMIT = Key.key(NAMESPACE, "team/set-captain-submit");
    public static final Key CLEAR_CAPTAIN = Key.key(NAMESPACE, "team/clear-captain");
    public static final Key OPEN_REMOVE_MEMBER = Key.key(NAMESPACE, "team/open-remove-member");
    public static final Key REMOVE_MEMBER_SUBMIT = Key.key(NAMESPACE, "team/remove-member-submit");
    public static final Key OPEN_SET_TIER_PICKER = Key.key(NAMESPACE, "team/open-set-tier-picker");
    public static final Key SET_TIER_PICK_SUBMIT = Key.key(NAMESPACE, "team/set-tier-pick-submit");
    public static final Key TIER_CLEAR = Key.key(NAMESPACE, "team/tier-clear");
    public static final Key OPEN_DELETE = Key.key(NAMESPACE, "team/open-delete");
    public static final Key DELETE_CONFIRM = Key.key(NAMESPACE, "team/delete");
    public static final Key CLOSE = Key.key(NAMESPACE, "team/close");

    public static final String INPUT_NAME = "team_name";
    public static final String INPUT_COLOR = "team_color";
    public static final String INPUT_USERNAME = "username";
    public static final String INPUT_MEMBER = "member";

    private static final String OPEN_TEAM_PREFIX = "team/open/";
    private static final String OPEN_SET_TIER_PREFIX = "team/open-set-tier/";
    private static final String TIER_SET_PREFIX = "team/tier-set/";

    private TeamAdminDialogKeys() {
    }

    public static Key openTeam(UUID teamId) {
        return Key.key(NAMESPACE, OPEN_TEAM_PREFIX + teamId);
    }

    public static Optional<UUID> parseOpenTeam(@Nullable Key key) {
        return parseUuidSuffix(key, OPEN_TEAM_PREFIX);
    }

    public static Key openSetTier(UUID accountId) {
        return Key.key(NAMESPACE, OPEN_SET_TIER_PREFIX + accountId);
    }

    public static Optional<UUID> parseOpenSetTier(@Nullable Key key) {
        return parseUuidSuffix(key, OPEN_SET_TIER_PREFIX);
    }

    public static Key setTier(PlayerTier tier) {
        return Key.key(NAMESPACE, TIER_SET_PREFIX + tier.value());
    }

    public static Optional<PlayerTier> parseSetTier(@Nullable Key key) {
        if (key == null || !NAMESPACE.equals(key.namespace())) {
            return Optional.empty();
        }
        String value = key.value();
        if (!value.startsWith(TIER_SET_PREFIX)) {
            return Optional.empty();
        }
        try {
            return PlayerTier.fromValue(Integer.parseInt(value.substring(TIER_SET_PREFIX.length())));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public static boolean isTeamAdminKey(@Nullable Key key) {
        return key != null
                && NAMESPACE.equals(key.namespace())
                && key.value().startsWith("team/");
    }

    private static Optional<UUID> parseUuidSuffix(@Nullable Key key, String prefix) {
        if (key == null || !NAMESPACE.equals(key.namespace())) {
            return Optional.empty();
        }
        String value = key.value();
        if (!value.startsWith(prefix)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value.substring(prefix.length())));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
