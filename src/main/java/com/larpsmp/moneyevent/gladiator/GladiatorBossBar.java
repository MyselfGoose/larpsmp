package com.larpsmp.moneyevent.gladiator;

import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Server-wide Gladiators points counter (boss bar).
 */
public final class GladiatorBossBar {

    private final GladiatorService service;
    private final TeamService teams;
    private @Nullable BossBar bossBar;

    public GladiatorBossBar(GladiatorService service, TeamService teams) {
        this.service = Objects.requireNonNull(service, "service");
        this.teams = Objects.requireNonNull(teams, "teams");
    }

    public void refresh() {
        Optional<GladiatorSession> sessionOpt = service.activeSession();
        if (sessionOpt.isEmpty() || !sessionOpt.get().phase().isLive()) {
            clear();
            return;
        }
        GladiatorSession session = sessionOpt.get();
        Component title = buildTitle(session);
        BossBar.Color color = switch (session.phase()) {
            case OPEN -> BossBar.Color.WHITE;
            case TEAM_FIGHT -> BossBar.Color.RED;
            case KIT_BAN -> BossBar.Color.YELLOW;
            case DUEL -> BossBar.Color.PURPLE;
            default -> BossBar.Color.BLUE;
        };
        if (bossBar == null) {
            bossBar = BossBar.bossBar(title, 1.0f, color, BossBar.Overlay.PROGRESS);
        } else {
            bossBar.name(title);
            bossBar.progress(1.0f);
            bossBar.color(color);
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }
    }

    public void clear() {
        if (bossBar == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bossBar);
        }
        bossBar = null;
    }

    public void showTo(Player player) {
        if (bossBar != null && player != null) {
            player.showBossBar(bossBar);
        }
    }

    private Component buildTitle(GladiatorSession session) {
        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(session.pointsByTeam().entrySet());
        ranked.sort(Comparator
                .<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(entry -> entry.getKey().toString()));
        StringBuilder scores = new StringBuilder();
        for (Map.Entry<UUID, Integer> entry : ranked) {
            if (!scores.isEmpty()) {
                scores.append(" | ");
            }
            String name = teams.getTeam(entry.getKey()).map(Team::displayName).orElse("?");
            scores.append(name).append(' ').append(entry.getValue());
        }
        String phaseLabel = switch (session.phase()) {
            case OPEN -> session.scheduleFinished() ? "OPEN (1v1)" : "OPEN (3v3 #"
                    + (session.currentPendingBout().map(b -> b.boutIndex() + 1).orElse(0)) + ")";
            case TEAM_FIGHT -> "FIGHT";
            case KIT_BAN -> "KIT BAN";
            case DUEL -> "DUEL";
            default -> session.phase().name();
        };
        String body = scores.isEmpty() ? phaseLabel : phaseLabel + " — " + scores;
        return Component.text("Gladiators: " + body, NamedTextColor.GOLD);
    }
}
