package com.larpsmp.moneyevent.capture;

import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.command.OnlinePlayerAccess;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.SettlementContribution;
import com.larpsmp.moneyevent.wallet.SplitRewardResult;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/**
 * Sends per-member capture reward chat cards (kill-credit style) to online winners.
 */
public final class CaptureRewardNotifier {

    private final AccountRepository accounts;
    private final TeamService teams;
    private final OnlinePlayerAccess onlinePlayers;
    private final Consumer<String> errorLogger;

    public CaptureRewardNotifier(
            AccountRepository accounts,
            TeamService teams,
            OnlinePlayerAccess onlinePlayers,
            Consumer<String> errorLogger) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers, "onlinePlayers");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public void notifyCompletion(
            CaptureSession session,
            CapturePointDefinition point,
            UUID winningTeamId,
            SplitRewardResult payout) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(winningTeamId, "winningTeamId");
        Objects.requireNonNull(payout, "payout");
        if (!payout.successful() || payout.shares().isEmpty()) {
            return;
        }

        String pointName = point != null ? point.displayName() : session.pointId();
        String teamName = teams.getTeam(winningTeamId).map(Team::displayName).orElse("Unknown");
        long pool = payout.amount() > 0 ? payout.amount() : 0L;

        for (SettlementContribution share : payout.shares()) {
            if (!share.credit() || share.amount() <= 0) {
                continue;
            }
            Component[] lines = CaptureCreditMessages.creditCard(
                    pointName,
                    teamName,
                    pool,
                    share.amount(),
                    share.balanceBefore(),
                    share.balanceAfter()
            );
            sendToAccount(share.accountId(), lines, session.captureId());
        }
    }

    private void sendToAccount(UUID accountId, Component[] lines, UUID captureId) {
        try {
            accounts.findIdentityByAccountId(accountId).ifPresent(identity -> {
                if (!onlinePlayers.isOnline(identity.minecraftUuid())) {
                    return;
                }
                for (Component line : lines) {
                    onlinePlayers.send(identity.minecraftUuid(), line);
                }
            });
        } catch (SQLException exception) {
            errorLogger.accept("Could not notify capture reward for " + captureId + " account "
                    + accountId + ": " + exception.getMessage());
        }
    }
}
