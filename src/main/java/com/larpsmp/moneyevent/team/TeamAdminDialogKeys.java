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
    public static final Key OPEN_EDIT = Key.key(NAMESPACE, "team/open-edit");
    public static final Key EDIT_SUBMIT = Key.key(NAMESPACE, "team/edit");
    public static final Key OPEN_MEMBERS = Key.key(NAMESPACE, "team/open-members");
    public static final Key OPEN_ASSIGN = Key.key(NAMESPACE, "team/open-assign");
    public static final Key ASSIGN_SUBMIT = Key.key(NAMESPACE, "team/assign");
    public static final Key OPEN_CAPTAIN = Key.key(NAMESPACE, "team/open-captain");
    public static final Key CLEAR_CAPTAIN = Key.key(NAMESPACE, "team/clear-captain");
    public static final Key OPEN_DELETE = Key.key(NAMESPACE, "team/open-delete");
    public static final Key DELETE_CONFIRM = Key.key(NAMESPACE, "team/delete");
    public static final Key CLOSE = Key.key(NAMESPACE, "team/close");

    public static final String INPUT_NAME = "team_name";
    public static final String INPUT_COLOR = "team_color";
    public static final String INPUT_USERNAME = "username";

    private static final String OPEN_TEAM_PREFIX = "team/open/";
    private static final String SET_CAPTAIN_PREFIX = "team/set-captain/";
    private static final String REMOVE_MEMBER_PREFIX = "team/remove-member/";

    private TeamAdminDialogKeys() {
    }

    public static Key openTeam(UUID teamId) {
        return Key.key(NAMESPACE, OPEN_TEAM_PREFIX + teamId);
    }

    public static Key setCaptain(UUID accountId) {
        return Key.key(NAMESPACE, SET_CAPTAIN_PREFIX + accountId);
    }

    public static Key removeMember(UUID accountId) {
        return Key.key(NAMESPACE, REMOVE_MEMBER_PREFIX + accountId);
    }

    public static Optional<UUID> parseOpenTeam(@Nullable Key key) {
        return parseUuidSuffix(key, OPEN_TEAM_PREFIX);
    }

    public static Optional<UUID> parseSetCaptain(@Nullable Key key) {
        return parseUuidSuffix(key, SET_CAPTAIN_PREFIX);
    }

    public static Optional<UUID> parseRemoveMember(@Nullable Key key) {
        return parseUuidSuffix(key, REMOVE_MEMBER_PREFIX);
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
