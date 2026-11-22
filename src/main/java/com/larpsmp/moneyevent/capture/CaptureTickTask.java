package com.larpsmp.moneyevent.capture;

import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Periodic capture progress + temp-block expiry tick.
 */
public final class CaptureTickTask extends BukkitRunnable {

    private final CaptureService captureService;
    private final CaptureOccupancyScanner occupancy;
    private final CaptureBossBarPresenter bossBar;
    private final CaptureTempBlockTracker tempBlocks;
    private final long deltaMillis;

    public CaptureTickTask(
            CaptureService captureService,
            CaptureOccupancyScanner occupancy,
            CaptureBossBarPresenter bossBar,
            CaptureTempBlockTracker tempBlocks) {
        this.captureService = Objects.requireNonNull(captureService, "captureService");
        this.occupancy = Objects.requireNonNull(occupancy, "occupancy");
        this.bossBar = Objects.requireNonNull(bossBar, "bossBar");
        this.tempBlocks = Objects.requireNonNull(tempBlocks, "tempBlocks");
        this.deltaMillis = captureService.config().tickSeconds() * 1000L;
    }

    @Override
    public void run() {
        tempBlocks.expireDue();

        var sessionOpt = captureService.activeSession();
        if (sessionOpt.isEmpty()) {
            bossBar.clear();
            return;
        }
        CaptureSession session = sessionOpt.get();
        var pointOpt = captureService.activePoint();
        if (pointOpt.isEmpty() || !pointOpt.get().isRegionDefined()) {
            bossBar.clear();
            return;
        }
        CapturePointDefinition point = pointOpt.get();
        CaptureAdvantage advantage = occupancy.scan(point, captureService.config().heightBlocks());
        if (session.isRunning()) {
            captureService.tick(advantage, deltaMillis);
        }
        var currentSession = captureService.activeSession();
        var currentPoint = captureService.activePoint();
        if (currentSession.isPresent() && currentPoint.isPresent() && currentPoint.get().isRegionDefined()) {
            CaptureAdvantage currentAdvantage = occupancy.scan(
                    currentPoint.get(), captureService.config().heightBlocks());
            bossBar.show(currentSession.get(), currentPoint.get(), currentAdvantage);
        } else {
            bossBar.clear();
        }
    }

    public void start(org.bukkit.plugin.Plugin plugin) {
        long periodTicks = Math.max(1L, captureService.config().tickSeconds() * 20L);
        runTaskTimer(plugin, periodTicks, periodTicks);
    }

    public static void broadcast(String message) {
        Bukkit.getServer().broadcast(net.kyori.adventure.text.Component.text(message));
    }
}
