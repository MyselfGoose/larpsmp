package com.larpsmp.moneyevent.auction;

import java.util.Objects;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Writes auction settings back into {@code config.yml} so the live file mirrors DB state.
 */
public final class AuctionConfigWriter {

    private AuctionConfigWriter() {
    }

    public static void write(JavaPlugin plugin, AuctionConfig config) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(config, "config");
        FileConfiguration yaml = plugin.getConfig();
        yaml.set("auction.payment-username", config.paymentUsername());
        plugin.saveConfig();
    }
}
