package com.larpsmp.moneyevent.team;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Marks inventories owned by the Teams Admin GUI so clicks can be handled safely.
 */
public final class TeamAdminMenu implements InventoryHolder {

    public enum View {
        HUB,
        TEAM,
        ASSIGN,
        DELETE_CONFIRM,
        WARNINGS
    }

    private final View view;
    private final @Nullable UUID teamId;
    private final int page;
    private Inventory inventory;

    public TeamAdminMenu(View view, @Nullable UUID teamId) {
        this(view, teamId, 0);
    }

    public TeamAdminMenu(View view, @Nullable UUID teamId, int page) {
        this.view = view;
        this.teamId = teamId;
        this.page = Math.max(0, page);
    }

    public View view() {
        return view;
    }

    public @Nullable UUID teamId() {
        return teamId;
    }

    public int page() {
        return page;
    }

    void attach(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
