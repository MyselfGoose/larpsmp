package com.larpsmp.moneyevent.team;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * PersistentData markers for Teams Admin inventory GUI items.
 */
public final class TeamAdminGuiKeys {

    public enum Action {
        OPEN_TEAM,
        CREATE,
        WARNINGS,
        CLOSE,
        BACK_HUB,
        ASSIGN,
        EDIT,
        DELETE,
        DELETE_CONFIRM,
        DELETE_CANCEL,
        REMOVE_MEMBER,
        SET_CAPTAIN,
        CLEAR_CAPTAIN,
        BACK_TEAM,
        WARNINGS_BACK
    }

    private final NamespacedKey actionKey;
    private final NamespacedKey teamKey;
    private final NamespacedKey accountKey;

    public TeamAdminGuiKeys(Plugin plugin) {
        this.actionKey = new NamespacedKey(plugin, "team_gui_action");
        this.teamKey = new NamespacedKey(plugin, "team_gui_team");
        this.accountKey = new NamespacedKey(plugin, "team_gui_account");
    }

    public void tag(ItemStack item, Action action, @Nullable java.util.UUID teamId, @Nullable java.util.UUID accountId) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.name());
        if (teamId != null) {
            meta.getPersistentDataContainer().set(teamKey, PersistentDataType.STRING, teamId.toString());
        }
        if (accountId != null) {
            meta.getPersistentDataContainer().set(accountKey, PersistentDataType.STRING, accountId.toString());
        }
        item.setItemMeta(meta);
    }

    public @Nullable Action actionOf(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return Action.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public @Nullable java.util.UUID teamIdOf(@Nullable ItemStack item) {
        return uuidOf(item, teamKey);
    }

    public @Nullable java.util.UUID accountIdOf(@Nullable ItemStack item) {
        return uuidOf(item, accountKey);
    }

    private @Nullable java.util.UUID uuidOf(@Nullable ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return java.util.UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
