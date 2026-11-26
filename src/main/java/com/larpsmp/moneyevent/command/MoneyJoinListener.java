package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.display.BalanceDisplayControl;
import com.larpsmp.moneyevent.notification.PaymentNotificationStore;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyService;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

public final class MoneyJoinListener implements Listener {
    private final MoneyService moneyService;
    private final @Nullable TeamService teams;
    private final PaymentNotificationStore notifications;
    private final Consumer<String> errorLogger;
    private final BalanceDisplayControl display;
    private final @Nullable AccountSessionManager accountSessions;

    public MoneyJoinListener(
            MoneyService moneyService,
            PaymentNotificationStore notifications,
            Consumer<String> errorLogger,
            BalanceDisplayControl display) {
        this(moneyService, null, notifications, errorLogger, display, null);
    }

    public MoneyJoinListener(
            MoneyService moneyService,
            PaymentNotificationStore notifications,
            Consumer<String> errorLogger,
            BalanceDisplayControl display,
            @Nullable AccountSessionManager accountSessions) {
        this(moneyService, null, notifications, errorLogger, display, accountSessions);
    }

    public MoneyJoinListener(
            MoneyService moneyService,
            @Nullable TeamService teams,
            PaymentNotificationStore notifications,
            Consumer<String> errorLogger,
            BalanceDisplayControl display,
            @Nullable AccountSessionManager accountSessions) {
        this.moneyService = Objects.requireNonNull(moneyService, "moneyService");
        this.teams = teams;
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
        this.display = Objects.requireNonNull(display, "display");
        this.accountSessions = accountSessions;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        try {
            // Persist the launcher nickname in account_minecraft_identities — not the rebound
            // account username — so portable bind metadata stays accurate after identity rename.
            moneyService.touchIdentityOnJoin(player.getUniqueId(), lastSeenMinecraftName(player));
        } catch (IOException exception) {
            errorLogger.accept("Could not update identity for " + player.getUniqueId()
                    + ": " + exception.getMessage());
        }
        retryCompetitorStartingBalance(player);
        notifications.deliver(player.getUniqueId(),
                message -> player.sendMessage(
                        BukkitOnlinePlayerAccess.component(message, MessageKind.SUCCESS)));
        display.playerJoined(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        display.playerQuit(event.getPlayer().getUniqueId());
    }

    private void retryCompetitorStartingBalance(Player player) {
        if (teams == null || accountSessions == null) {
            return;
        }
        Optional<AccountSession> session = accountSessions.findByMinecraftUuid(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        if (!teams.isCompetitor(session.get().accountId())) {
            return;
        }
        try {
            moneyService.grantCompetitorStartingBalanceIfNeeded(session.get().accountId());
        } catch (IOException exception) {
            errorLogger.accept("Could not retry competitor starting balance for "
                    + session.get().accountId() + ": " + exception.getMessage());
        }
    }

    private String lastSeenMinecraftName(Player player) {
        if (accountSessions != null) {
            return accountSessions.findByMinecraftUuid(player.getUniqueId())
                    .map(session -> {
                        String launcher = session.launcherName();
                        if (launcher != null && !launcher.isBlank()) {
                            return launcher;
                        }
                        return session.username();
                    })
                    .orElseGet(player::getName);
        }
        return player.getName();
    }
}
