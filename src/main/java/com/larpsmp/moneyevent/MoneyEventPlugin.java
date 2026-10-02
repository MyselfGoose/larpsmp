package com.larpsmp.moneyevent;

import org.bukkit.plugin.java.JavaPlugin;

public final class MoneyEventPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Money Event plugin enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Money Event plugin disabled.");
    }
}
