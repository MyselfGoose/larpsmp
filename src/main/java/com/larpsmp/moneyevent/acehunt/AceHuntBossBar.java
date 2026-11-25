package com.larpsmp.moneyevent.acehunt;

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
 * Server-wide Ace Hunt points counter (boss bar).
 */
public final class AceHuntBossBar {

    private final AceHuntService service;
    private final TeamService teams;
    private @Nullable BossBar bossBar;

    public AceHuntBossBar(AceHuntService service, TeamService teams) {
        this.service = Objects.requireNonNull(service, "service");
        this.teams = Objects.requireNonNull(teams, "teams");
    }

    public void refresh() {
        Optional<AceHuntSession> sessionOpt = service.activeSession();
        if (sessionOpt.isEmpty() || !sessionOpt.get().phase().isLive()) {
            clear();
            return;
        }
        AceHuntSession session = sessionOpt.get();
        AceHuntScoreboard board = AceHuntScoreboard.from(session);
        Component title = buildTitle(session.phase(), board);
        float progress = phaseProgress(session);
        BossBar.Color color = switch (session.phase()) {
            case PREP -> BossBar.Color.WHITE;
            case HUNT -> BossBar.Color.RED;
            case SURFACE -> BossBar.Color.YELLOW;
            case BAN -> BossBar.Color.PURPLE;
            default -> BossBar.Color.BLUE;
        };
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

    private Component buildTitle(AceHuntPhase phase, AceHuntScoreboard board) {
        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>(board.totals().entrySet());
        ranked.sort(Comparator
                .<Map.Entry<UUID, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(entry -> entry.getKey().toString()));
        var builder = Component.text()
                .append(Component.text("Ace Hunt ", NamedTextColor.GOLD))
                .append(Component.text(phase.name(), NamedTextColor.YELLOW))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY));
        if (ranked.isEmpty()) {
            builder.append(Component.text("—", NamedTextColor.GRAY));
            return builder.build();
        }
        for (int i = 0; i < ranked.size(); i++) {
            if (i > 0) {
                builder.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            }
            Map.Entry<UUID, Integer> entry = ranked.get(i);
            String name = teams.getTeam(entry.getKey()).map(Team::displayName).orElse("?");
            int hunt = board.huntPoints().getOrDefault(entry.getKey(), 0);
            int survival = board.survivalPoints().getOrDefault(entry.getKey(), 0);
            builder.append(Component.text(name, NamedTextColor.AQUA))
                    .append(Component.text(" " + entry.getValue(), NamedTextColor.WHITE))
                    .append(Component.text(" (H" + hunt + "/S" + survival + ")", NamedTextColor.GRAY));
        }
        return builder.build();
    }

    private float phaseProgress(AceHuntSession session) {
        AceHuntConfig config = service.config();
        return switch (session.phase()) {
            case HUNT -> {
                if (session.huntStartedAt() == null) {
                    yield 1.0f;
                }
                long elapsed = java.time.Duration.between(
                        session.huntStartedAt(), java.time.Instant.now()).toMillis();
                long total = config.huntDuration().toMillis();
                yield (float) Math.max(0.0, Math.min(1.0, 1.0 - (elapsed / (double) total)));
            }
            case SURFACE -> {
                if (session.surfaceStartedAt() == null) {
                    yield 1.0f;
                }
                long elapsed = java.time.Duration.between(
                        session.surfaceStartedAt(), java.time.Instant.now()).toMillis();
                long total = config.surfaceWarning().toMillis();
                yield (float) Math.max(0.0, Math.min(1.0, 1.0 - (elapsed / (double) total)));
            }
            case BAN -> 0.15f;
            case PREP -> 1.0f;
            default -> 0.0f;
        };
    }
}
