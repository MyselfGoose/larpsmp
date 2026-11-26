package com.larpsmp.moneyevent.gladiator;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Player-facing Gladiators commands ({@code /gladiator}).
 */
public final class GladiatorPlayerCommandController {

    private final GladiatorService service;
    private final TeamService teams;
    private final AccountSessionManager sessions;

    public GladiatorPlayerCommandController(
            GladiatorService service,
            TeamService teams,
            AccountSessionManager sessions
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    public boolean handle(CommandSource source, String[] arguments) {
        Optional<UUID> accountId = resolveAccount(source);
        if (accountId.isEmpty()) {
            source.send("You must be logged in to use /gladiator.", MessageKind.ERROR);
            return true;
        }
        if (arguments.length >= 1 && arguments[0].equalsIgnoreCase("ban")) {
            if (arguments.length < 2) {
                source.send("Usage: /gladiator ban <kit>", MessageKind.ERROR);
                return true;
            }
            GladiatorService.AdminResult result = service.banKit(accountId.get(), arguments[1]);
            source.send(result.message(), result.success() ? MessageKind.SUCCESS : MessageKind.ERROR);
            return true;
        }

        Optional<GladiatorSession> session = service.activeSession();
        if (session.isEmpty()) {
            source.send("No Gladiators session is active.", MessageKind.INFO);
            return true;
        }
        int used = session.get().fightsUsed(accountId.get());
        source.send("Your fights: " + used + "/" + GladiatorRules.MAX_FIGHTS, MessageKind.INFO);
        Optional<Team> team = teams.teamOf(accountId.get());
        if (team.isPresent()) {
            int points = session.get().pointsByTeam().getOrDefault(team.get().id(), 0);
            source.send("Team " + team.get().displayName() + " points: " + points, MessageKind.INFO);
        }
        Map<UUID, Integer> board = session.get().pointsByTeam();
        if (!board.isEmpty()) {
            source.send("Standings:", MessageKind.INFO);
            for (Map.Entry<UUID, Integer> entry : board.entrySet()) {
                String name = teams.getTeam(entry.getKey()).map(Team::displayName).orElse("?");
                source.send("  " + name + ": " + entry.getValue(), MessageKind.INFO);
            }
        }
        session.get().duelPending().ifPresent(pending -> {
            if (pending.nextBanAccountId().equals(accountId.get())) {
                source.send("It is your turn to ban a kit: /gladiator ban <1-5>", MessageKind.INFO);
            }
        });
        return true;
    }

    public List<String> tabComplete(CommandSource source, String[] arguments) {
        if (arguments.length == 1) {
            return filterPrefix(List.of("ban"), arguments[0]);
        }
        if (arguments.length == 2 && arguments[0].equalsIgnoreCase("ban")) {
            return filterPrefix(List.of("1", "2", "3", "4", "5"), arguments[1]);
        }
        return List.of();
    }

    private Optional<UUID> resolveAccount(CommandSource source) {
        return source.playerId().flatMap(minecraftUuid ->
                sessions.findByMinecraftUuid(minecraftUuid).map(AccountSession::accountId));
    }

    private static List<String> filterPrefix(List<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(needle))
                .toList();
    }
}
