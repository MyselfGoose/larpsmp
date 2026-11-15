package com.larpsmp.moneyevent.team;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * PDC-tagged Nether Star used as the Teams Admin hotbar shortcut.
 */
public final class TeamAdminTool {

    public static final int PREFERRED_SLOT = 8;

    private final NamespacedKey toolKey;

    public TeamAdminTool(Plugin plugin) {
        // Stable cross-version key; plugin arg reserved for future namespaced registration.
        this.toolKey = new NamespacedKey("larpsmp", "teams_admin_tool");
    }

    public NamespacedKey key() {
        return toolKey;
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Teams Admin", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Right-click to manage teams", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Ops only", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTool(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    /**
     * Removes every Teams Admin tool from the inventory. Returns how many stacks were removed.
     */
    public int stripAll(PlayerInventory inventory) {
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isTool(contents[slot])) {
                inventory.setItem(slot, null);
                removed++;
            }
        }
        if (isTool(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(ItemStack.empty());
            removed++;
        }
        return removed;
    }

    /**
     * Grants the tool to an op inventory without overwriting a real item in slot 8.
     *
     * @return true if the tool was placed somewhere in the hotbar/inventory
     */
    public boolean grantPreferringSlot8(PlayerInventory inventory) {
        stripAll(inventory);
        ItemStack tool = create();
        ItemStack preferred = inventory.getItem(PREFERRED_SLOT);
        if (preferred == null || preferred.getType().isAir()) {
            inventory.setItem(PREFERRED_SLOT, tool);
            return true;
        }
        int empty = inventory.firstEmpty();
        if (empty >= 0 && empty < 9) {
            inventory.setItem(empty, tool);
            return true;
        }
        if (empty >= 0) {
            inventory.setItem(empty, tool);
            return true;
        }
        return false;
    }

    /**
     * Returns a copy of inventory contents with Teams Admin tools replaced by air (for body capture).
     */
    public ItemStack[] withoutTools(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            copy[i] = isTool(stack) ? null : (stack == null ? null : stack.clone());
        }
        return copy;
    }
}
