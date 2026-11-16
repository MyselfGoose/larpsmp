package com.larpsmp.moneyevent.capture;

import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
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
 * Server-wide boss bar for the active capture point.
 */
public final class CaptureBossBarPresenter {

    private final TeamService teams;
    private @Nullable BossBar bossBar;

    public CaptureBossBarPresenter(TeamService teams) {
        this.teams = Objects.requireNonNull(teams, "teams");
    }

    public void show(CaptureSession session, CapturePointDefinition point, CaptureAdvantage advantage) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(point, "point");
        float progress = (float) Math.max(0.0, Math.min(1.0, session.progress().progressFraction()));
        String owner = teamLabel(session.progress().owningTeamId());
        String lead = advantage.empty()
                ? "empty"
                : advantage.tied()
                ? "tied"
                : teamLabel(advantage.leadingTeamId()) + " +" + advantage.advantage();
        Component title = Component.text()
                .append(Component.text(point.displayName(), NamedTextColor.GOLD))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(session.progress().progressPercent() + "%", NamedTextColor.WHITE))
                .append(Component.text("  owner=", NamedTextColor.GRAY))
                .append(Component.text(owner, NamedTextColor.AQUA))
                .append(Component.text("  lead=", NamedTextColor.GRAY))
                .append(Component.text(lead, NamedTextColor.YELLOW))
                .build();
        BossBar.Color color = session.isPaused() ? BossBar.Color.YELLOW : BossBar.Color.BLUE;
        if (bossBar == null) {
            bossBar = BossBar.bossBar(title, progress, color, BossBar.Overlay.PROGRESS);
        } else {
            bossBar.name(title);
            bossBar.progress(progress);
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

    private String teamLabel(@Nullable UUID teamId) {
        if (teamId == null) {
            return "none";
        }
        Optional<Team> team = teams.getTeam(teamId);
        return team.map(Team::displayName).orElse(teamId.toString().substring(0, 8));
    }
}
