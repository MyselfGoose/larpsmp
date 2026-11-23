package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamMember;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyFormats;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

/**
 * Player-facing team info: {@code /team} roster and {@code /team balance}.
 */
public final class TeamInfoCommandController {
    private final MoneyService money;
    private final TeamService teams;
    private final Consumer<String> errorLogger;

    public TeamInfoCommandController(
            MoneyService money,
            TeamService teams,
            Consumer<String> errorLogger
    ) {
        this.money = money;
        this.teams = teams;
        this.errorLogger = errorLogger;
    }

    public void team(CommandSource source, String[] arguments) {
        if (arguments.length == 0) {
            showRoster(source);
            return;
        }
        if (arguments.length == 1 && arguments[0].equalsIgnoreCase("balance")) {
            showBalance(source);
            return;
        }
        source.send("Usage: /team [balance]", MessageKind.ERROR);
    }

    public List<String> tabComplete(String[] arguments) {
        if (arguments.length == 1) {
            String prefix = arguments[0].toLowerCase(Locale.ROOT);
            if ("balance".startsWith(prefix)) {
                return List.of("balance");
            }
        }
        return List.of();
    }

    private void showRoster(CommandSource source) {
        ResolvedTeam resolved = resolveCallerTeam(source);
        if (resolved == null) {
            return;
        }
        Team team = resolved.team();
        TextColor color = TextColor.color(team.color().rgb());
        source.send(Component.text()
                .append(Component.text(team.displayName(), color, TextDecoration.BOLD))
                .append(Component.text(" roster", NamedTextColor.GRAY))
                .build());

        String captain = teams.captainUsername(team).orElse("—");
        source.send(Component.text()
                .append(Component.text("Captain: ", NamedTextColor.GRAY))
                .append(Component.text(captain, NamedTextColor.WHITE))
                .build());

        try {
            if (money.teamTotalsVisible()) {
                long wealth = money.teamWealth(team.id());
                source.send(Component.text()
                        .append(Component.text("Team Money: ", NamedTextColor.AQUA))
                        .append(Component.text(MoneyFormats.currency(wealth), NamedTextColor.AQUA))
                        .build());
            } else {
                source.send(Component.text("Team Money: hidden", NamedTextColor.DARK_GRAY));
            }
        } catch (IOException exception) {
            errorLogger.accept("Could not load team wealth for /team: " + exception.getMessage());
            source.send("Team Money: unavailable", MessageKind.ERROR);
        }

        List<TeamMember> members = new ArrayList<>(teams.membersOf(team.id()));
        members.sort(Comparator
                .comparingInt((TeamMember member) -> member.tier() == null ? 99 : member.tier())
                .thenComparing(TeamMember::username, String.CASE_INSENSITIVE_ORDER));
        if (members.isEmpty()) {
            source.send("No members assigned yet.", MessageKind.INFO);
            return;
        }
        for (TeamMember member : members) {
            String tierLabel = member.playerTier().map(PlayerTier::shortLabel).orElse("—");
            source.send(Component.text()
                    .append(Component.text("• ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(member.username(), NamedTextColor.WHITE))
                    .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                    .append(Component.text(tierLabel, NamedTextColor.GRAY))
                    .append(Component.text(")", NamedTextColor.DARK_GRAY))
                    .build());
        }
    }

    private void showBalance(CommandSource source) {
        ResolvedTeam resolved = resolveCallerTeam(source);
        if (resolved == null) {
            return;
        }
        try {
            if (!money.teamTotalsVisible()) {
                source.send("Team totals are currently hidden.", MessageKind.INFO);
                return;
            }
            long wealth = money.teamWealth(resolved.team().id());
            source.send(Component.text()
                    .append(Component.text(resolved.team().displayName(),
                            TextColor.color(resolved.team().color().rgb())))
                    .append(Component.text(" Team Money: ", NamedTextColor.AQUA))
                    .append(Component.text(MoneyFormats.currency(wealth), NamedTextColor.AQUA))
                    .build());
        } catch (IOException exception) {
            errorLogger.accept("Could not load team wealth for /team balance: " + exception.getMessage());
            source.send("Team Money could not be loaded.", MessageKind.ERROR);
        }
    }

    private @Nullable ResolvedTeam resolveCallerTeam(CommandSource source) {
        Optional<UUID> playerId = source.playerId();
        if (playerId.isEmpty()) {
            source.send("Console cannot use /team. Use /larp team … for administration.", MessageKind.ERROR);
            return null;
        }
        try {
            Optional<WalletAccount> wallet = money.account(playerId.get());
            if (wallet.isEmpty()) {
                source.send("You do not have a wallet yet.", MessageKind.ERROR);
                return null;
            }
            Optional<Team> team = teams.teamOf(wallet.get().accountId());
            if (team.isEmpty()) {
                source.send("You are not on a team.", MessageKind.INFO);
                return null;
            }
            return new ResolvedTeam(team.get(), wallet.get().accountId());
        } catch (IOException exception) {
            errorLogger.accept("Could not resolve /team caller: " + exception.getMessage());
            source.send("Could not load your team. Please contact an admin.", MessageKind.ERROR);
            return null;
        }
    }

    private record ResolvedTeam(Team team, UUID accountId) {
    }
}
