package com.larpsmp.moneyevent.acehunt;

import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

/**
 * One-second Ace Hunt tick: phase transitions, lockup release, underground ban damage, boss bar.
 */
public final class AceHuntTickTask {

    private final AceHuntService service;
    private final AceHuntBossBar bossBar;
    private final AccountSessionManager sessions;
    private @Nullable BukkitTask task;

    public AceHuntTickTask(
            AceHuntService service,
            AceHuntBossBar bossBar,
            AccountSessionManager sessions
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.bossBar = Objects.requireNonNull(bossBar, "bossBar");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public void start(Plugin plugin) {
        cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        service.tick();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Optional<AccountSession> session = sessions.findByMinecraftUuid(player.getUniqueId());
            if (session.isEmpty()) {
                continue;
            }
            Location location = player.getLocation();
            if (location.getWorld() == null) {
                continue;
            }
            service.damageIfUnderground(
                    session.get().accountId(),
                    location.getWorld().getName(),
                    location.getY());
        }
        if (service.activeSession().isPresent()) {
            bossBar.refresh();
        } else {
            bossBar.clear();
        }
    }
}
