package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.display.BalanceDisplayControl;
import com.larpsmp.moneyevent.notification.PaymentNotificationStore;
import com.larpsmp.moneyevent.wallet.MoneyService;
import java.io.IOException;
import java.util.function.Consumer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class MoneyJoinListener implements Listener {
    private final MoneyService moneyService;
    private final PaymentNotificationStore notifications;
    private final Consumer<String> errorLogger;
    private final BalanceDisplayControl display;

    public MoneyJoinListener(
            MoneyService moneyService,
            PaymentNotificationStore notifications,
            Consumer<String> errorLogger,
            BalanceDisplayControl display) {
        this.moneyService = moneyService;
        this.notifications = notifications;
        this.errorLogger = errorLogger;
        this.display = display;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        try {
            moneyService.touchIdentityOnJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        } catch (IOException exception) {
            errorLogger.accept("Could not update identity for " + event.getPlayer().getUniqueId()
                    + ": " + exception.getMessage());
        }
        notifications.deliver(event.getPlayer().getUniqueId(),
                message -> event.getPlayer().sendMessage(
                        BukkitOnlinePlayerAccess.component(message, MessageKind.SUCCESS)));
        display.playerJoined(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        display.playerQuit(event.getPlayer().getUniqueId());
    }
}
