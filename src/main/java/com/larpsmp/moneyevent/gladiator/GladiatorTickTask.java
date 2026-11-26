package com.larpsmp.moneyevent.gladiator;

import java.util.Objects;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

/**
 * Refreshes the Gladiators boss bar while a session is live.
 */
public final class GladiatorTickTask {

    private final GladiatorService service;
    private final GladiatorBossBar bossBar;
    private @Nullable BukkitTask task;

    public GladiatorTickTask(GladiatorService service, GladiatorBossBar bossBar) {
        this.service = Objects.requireNonNull(service, "service");
        this.bossBar = Objects.requireNonNull(bossBar, "bossBar");
    }

    public void start(Plugin plugin) {
        cancel();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, bossBar::refresh, 20L, 20L);
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        bossBar.clear();
    }
}
