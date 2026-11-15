package com.larpsmp.moneyevent.combat;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;

/**
 * Blocks whose placer is tracked for kill attribution (lava, fire, TNT, traps).
 */
public final class OwnedHazardMaterials {

    private static final Set<Material> TRACKED = EnumSet.of(
            Material.LAVA,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.TNT,
            Material.DISPENSER,
            Material.DROPPER,
            Material.TRIPWIRE_HOOK,
            Material.TRIPWIRE,
            Material.STONE_PRESSURE_PLATE,
            Material.OAK_PRESSURE_PLATE,
            Material.SPRUCE_PRESSURE_PLATE,
            Material.BIRCH_PRESSURE_PLATE,
            Material.JUNGLE_PRESSURE_PLATE,
            Material.ACACIA_PRESSURE_PLATE,
            Material.DARK_OAK_PRESSURE_PLATE,
            Material.MANGROVE_PRESSURE_PLATE,
            Material.CHERRY_PRESSURE_PLATE,
            Material.BAMBOO_PRESSURE_PLATE,
            Material.CRIMSON_PRESSURE_PLATE,
            Material.WARPED_PRESSURE_PLATE,
            Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,
            Material.STONE_BUTTON,
            Material.OAK_BUTTON,
            Material.SPRUCE_BUTTON,
            Material.BIRCH_BUTTON,
            Material.JUNGLE_BUTTON,
            Material.ACACIA_BUTTON,
            Material.DARK_OAK_BUTTON,
            Material.MANGROVE_BUTTON,
            Material.CHERRY_BUTTON,
            Material.BAMBOO_BUTTON,
            Material.CRIMSON_BUTTON,
            Material.WARPED_BUTTON,
            Material.POLISHED_BLACKSTONE_BUTTON,
            Material.LEVER,
            Material.REDSTONE_WIRE,
            Material.REPEATER,
            Material.COMPARATOR,
            Material.OBSERVER,
            Material.PISTON,
            Material.STICKY_PISTON,
            Material.DAYLIGHT_DETECTOR,
            Material.TARGET,
            Material.SCULK_SENSOR,
            Material.CALIBRATED_SCULK_SENSOR
    );

    private OwnedHazardMaterials() {
    }

    public static boolean isTracked(Material material) {
        return material != null && TRACKED.contains(material);
    }

    public static AttributionCause causeFor(Material material) {
        if (material == Material.LAVA) {
            return AttributionCause.LAVA;
        }
        if (material == Material.FIRE || material == Material.SOUL_FIRE) {
            return AttributionCause.FIRE;
        }
        if (material == Material.TNT) {
            return AttributionCause.EXPLOSION;
        }
        return AttributionCause.TRAP;
    }
}
