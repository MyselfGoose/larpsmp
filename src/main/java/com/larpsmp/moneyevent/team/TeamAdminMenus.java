package com.larpsmp.moneyevent.team;

import com.larpsmp.moneyevent.auth.Account;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

/**
 * Builds polished chest menus for Teams Admin browsing and management.
 *
 * <p>Create / rename still use Paper Dialogs. Assign uses a clickable player picker
 * (dialog text fields cannot offer command-style tab completion).
 */
public final class TeamAdminMenus {

    private static final TextColor ACCENT = TextColor.color(0xE0B040);
    private static final int[] TEAM_SLOTS = {10, 12, 14, 16, 28, 30, 32, 34};
    private static final int[] MEMBER_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] ASSIGN_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final TeamService teams;
    private final TeamAdminGuiKeys keys;

    public TeamAdminMenus(TeamService teams, TeamAdminGuiKeys keys) {
        this.teams = teams;
        this.keys = keys;
    }

    public Inventory hub(@Nullable String statusMessage) {
        TeamAdminMenu holder = new TeamAdminMenu(TeamAdminMenu.View.HUB, null);
        Inventory inventory = Bukkit.createInventory(holder, 45, Component.text("Teams Admin", ACCENT));
        holder.attach(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        List<String> warnings = teams.rosterWarnings();
        inventory.setItem(4, infoItem(
                Material.NETHER_STAR,
                Component.text("Teams Admin", ACCENT).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Click a team to manage its roster.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Competition target: 3 teams × 4 players.", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        warnings.isEmpty()
                                ? Component.text("Roster looks healthy.", NamedTextColor.GREEN)
                                .decoration(TextDecoration.ITALIC, false)
                                : Component.text(warnings.size() + " setup warning(s) — see below.", NamedTextColor.GOLD)
                                .decoration(TextDecoration.ITALIC, false),
                        statusMessage == null || statusMessage.isBlank()
                                ? Component.empty()
                                : Component.text(statusMessage, NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)
                )
        ));

        List<Team> teamList = teams.listTeams();
        for (int i = 0; i < teamList.size() && i < TEAM_SLOTS.length; i++) {
            Team team = teamList.get(i);
            inventory.setItem(TEAM_SLOTS[i], teamIcon(team, TeamAdminGuiKeys.Action.OPEN_TEAM));
        }

        ItemStack create = named(
                Material.LIME_CONCRETE,
                Component.text("Create Team", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Add a new competing team", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        keys.tag(create, TeamAdminGuiKeys.Action.CREATE, null, null);
        inventory.setItem(38, create);

        ItemStack warn = named(
                Material.BELL,
                Component.text("Warnings", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text(
                                warnings.isEmpty() ? "No roster warnings" : warnings.size() + " warning(s)",
                                NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        keys.tag(warn, TeamAdminGuiKeys.Action.WARNINGS, null, null);
        inventory.setItem(40, warn);

        ItemStack close = named(
                Material.BARRIER,
                Component.text("Close", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of());
        keys.tag(close, TeamAdminGuiKeys.Action.CLOSE, null, null);
        inventory.setItem(42, close);

        return inventory;
    }

    public Inventory team(Team team, @Nullable String statusMessage) {
        TeamAdminMenu holder = new TeamAdminMenu(TeamAdminMenu.View.TEAM, team.id());
        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                Component.text(team.displayName(), TextColor.color(team.color().rgb())));
        holder.attach(inventory);
        fill(inventory, paneFor(team.color()));

        List<TeamMember> members = teams.membersOf(team.id());
        String captain = teams.captainUsername(team).orElse("None");

        inventory.setItem(4, teamIcon(team, null));
        if (statusMessage != null && !statusMessage.isBlank()) {
            inventory.setItem(8, named(
                    Material.PAPER,
                    Component.text("Notice", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                    List.of(Component.text(statusMessage, NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))));
        }

        for (int i = 0; i < MEMBER_SLOTS.length; i++) {
            if (i < members.size()) {
                inventory.setItem(MEMBER_SLOTS[i], memberHead(team, members.get(i)));
            } else {
                inventory.setItem(MEMBER_SLOTS[i], named(
                        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        Component.text("Empty slot", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        List.of(Component.text((i + 1) + " of " + TeamService.EXPECTED_ROSTER_SIZE,
                                        NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false))));
            }
        }

        ItemStack assign = named(
                Material.EMERALD,
                Component.text("Assign Player", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Pick from registered accounts", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        keys.tag(assign, TeamAdminGuiKeys.Action.ASSIGN, team.id(), null);
        inventory.setItem(29, assign);

        ItemStack edit = named(
                Material.NAME_TAG,
                Component.text("Edit Team", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Rename or change color", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        keys.tag(edit, TeamAdminGuiKeys.Action.EDIT, team.id(), null);
        inventory.setItem(31, edit);

        ItemStack clearCaptain = named(
                Material.GOLDEN_HELMET,
                Component.text("Clear Captain", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Current: " + captain, NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Right-click a member head to set captain", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false)));
        keys.tag(clearCaptain, TeamAdminGuiKeys.Action.CLEAR_CAPTAIN, team.id(), null);
        inventory.setItem(33, clearCaptain);

        ItemStack delete = named(
                Material.TNT,
                Component.text("Delete Team", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Removes all memberships", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        keys.tag(delete, TeamAdminGuiKeys.Action.DELETE, team.id(), null);
        inventory.setItem(35, delete);

        ItemStack back = named(
                Material.ARROW,
                Component.text("Back", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                List.of());
        keys.tag(back, TeamAdminGuiKeys.Action.BACK_HUB, null, null);
        inventory.setItem(49, back);

        inventory.setItem(45, named(
                Material.BOOK,
                Component.text("Roster tips", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("Left-click member → remove", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Right-click member → set captain", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text(members.size() + "/" + TeamService.EXPECTED_ROSTER_SIZE + " filled",
                                        NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false))));

        return inventory;
    }

    public Inventory assign(Team team, int page) {
        List<Account> candidates = sortedAssignableAccounts();
        int pageSize = ASSIGN_SLOTS.length;
        int maxPage = Math.max(0, (candidates.size() - 1) / pageSize);
        int safePage = Math.min(Math.max(0, page), maxPage);

        TeamAdminMenu holder = new TeamAdminMenu(TeamAdminMenu.View.ASSIGN, team.id(), safePage);
        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                Component.text("Assign → " + team.displayName(), TextColor.color(team.color().rgb())));
        holder.attach(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        Set<UUID> onThisTeam = new HashSet<>();
        for (TeamMember member : teams.membersOf(team.id())) {
            onThisTeam.add(member.accountId());
        }

        inventory.setItem(4, named(
                concreteFor(team.color()),
                Component.text("Assign to " + team.displayName(), TextColor.color(team.color().rgb()))
                        .decoration(TextDecoration.ITALIC, false)
                        .decorate(TextDecoration.BOLD),
                List.of(
                        Component.text("Click a player to assign or move them.", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text(candidates.size() + " registered account(s)", NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.ITALIC, false))));

        int start = safePage * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int index = start + i;
            if (index >= candidates.size()) {
                break;
            }
            Account account = candidates.get(index);
            inventory.setItem(ASSIGN_SLOTS[i], assignCandidateHead(team, account, onThisTeam.contains(account.id())));
        }

        if (safePage > 0) {
            ItemStack prev = named(
                    Material.ARROW,
                    Component.text("Previous", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                    List.of(Component.text("Page " + safePage + " / " + (maxPage + 1), NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            keys.tag(prev, TeamAdminGuiKeys.Action.ASSIGN_PAGE_PREV, team.id(), null);
            inventory.setItem(45, prev);
        }

        ItemStack back = named(
                Material.ARROW,
                Component.text("Back", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                List.of());
        keys.tag(back, TeamAdminGuiKeys.Action.BACK_TEAM, team.id(), null);
        inventory.setItem(49, back);

        if (safePage < maxPage) {
            ItemStack next = named(
                    Material.ARROW,
                    Component.text("Next", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                    List.of(Component.text("Page " + (safePage + 2) + " / " + (maxPage + 1), NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false)));
            keys.tag(next, TeamAdminGuiKeys.Action.ASSIGN_PAGE_NEXT, team.id(), null);
            inventory.setItem(53, next);
        }

        ItemStack type = named(
                Material.NAME_TAG,
                Component.text("Type username…", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
                List.of(Component.text("Open a picker list if you prefer typing/selecting", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        keys.tag(type, TeamAdminGuiKeys.Action.ASSIGN_TYPE, team.id(), null);
        inventory.setItem(51, type);

        if (candidates.isEmpty()) {
            inventory.setItem(22, named(
                    Material.BARRIER,
                    Component.text("No accounts yet", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                    List.of(Component.text("Players must sign up before they can be assigned.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))));
        }

        return inventory;
    }

    public Inventory deleteConfirm(Team team) {
        TeamAdminMenu holder = new TeamAdminMenu(TeamAdminMenu.View.DELETE_CONFIRM, team.id());
        Inventory inventory = Bukkit.createInventory(
                holder, 27, Component.text("Delete " + team.displayName() + "?", NamedTextColor.RED));
        holder.attach(inventory);
        fill(inventory, Material.BLACK_STAINED_GLASS_PANE);

        int members = teams.membersOf(team.id()).size();
        inventory.setItem(13, named(
                Material.TNT,
                Component.text("Delete " + team.displayName() + "?", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false),
                List.of(
                        Component.text("This removes " + members + " membership(s).", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("This cannot be undone.", NamedTextColor.DARK_RED)
                                .decoration(TextDecoration.ITALIC, false))));

        ItemStack confirm = named(
                Material.RED_CONCRETE,
                Component.text("Confirm Delete", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false),
                List.of());
        keys.tag(confirm, TeamAdminGuiKeys.Action.DELETE_CONFIRM, team.id(), null);
        inventory.setItem(11, confirm);

        ItemStack cancel = named(
                Material.LIME_CONCRETE,
                Component.text("Cancel", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                List.of());
        keys.tag(cancel, TeamAdminGuiKeys.Action.DELETE_CANCEL, team.id(), null);
        inventory.setItem(15, cancel);

        return inventory;
    }

    public Inventory warnings() {
        TeamAdminMenu holder = new TeamAdminMenu(TeamAdminMenu.View.WARNINGS, null);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text("Roster Warnings", ACCENT));
        holder.attach(inventory);
        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        List<String> warnings = teams.rosterWarnings();
        if (warnings.isEmpty()) {
            inventory.setItem(22, named(
                    Material.LIME_CONCRETE,
                    Component.text("All clear", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false),
                    List.of(Component.text("No roster setup warnings.", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false))));
        } else {
            int slot = 10;
            for (String warning : warnings) {
                if (slot > 34) {
                    break;
                }
                if (slot % 9 == 8) {
                    slot += 2;
                }
                inventory.setItem(slot, named(
                        Material.ORANGE_CONCRETE,
                        Component.text("Warning", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false),
                        wrapLore(warning, NamedTextColor.GRAY)));
                slot++;
            }
        }

        ItemStack back = named(
                Material.ARROW,
                Component.text("Back", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                List.of());
        keys.tag(back, TeamAdminGuiKeys.Action.WARNINGS_BACK, null, null);
        inventory.setItem(49, back);
        return inventory;
    }

    private ItemStack teamIcon(Team team, @Nullable TeamAdminGuiKeys.Action action) {
        List<TeamMember> members = teams.membersOf(team.id());
        String captain = teams.captainUsername(team).orElse("No captain");
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(members.size() + "/" + TeamService.EXPECTED_ROSTER_SIZE + " members", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Captain: " + captain, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Color: " + team.color().displayName(), NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (!members.isEmpty()) {
            lore.add(Component.empty());
            for (TeamMember member : members) {
                boolean isCaptain = team.captainAccountId() != null
                        && team.captainAccountId().equals(member.accountId());
                lore.add(Component.text(
                                (isCaptain ? "★ " : "• ") + member.username(),
                                isCaptain ? NamedTextColor.GOLD : NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }
        if (action != null) {
            lore.add(Component.empty());
            lore.add(Component.text("Click to manage", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        }
        ItemStack item = named(
                concreteFor(team.color()),
                Component.text(team.displayName(), TextColor.color(team.color().rgb()))
                        .decoration(TextDecoration.ITALIC, false)
                        .decorate(TextDecoration.BOLD),
                lore);
        if (action != null) {
            keys.tag(item, action, team.id(), null);
        }
        return item;
    }

    private ItemStack assignCandidateHead(Team targetTeam, Account account, boolean alreadyOnTeam) {
        Optional<Team> currentTeam = teams.teamOf(account.id());
        boolean online = Bukkit.getOnlinePlayers().stream()
                .anyMatch(player -> player.getName().equalsIgnoreCase(account.username()));

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.displayName(Component.text(account.username(), online ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        Player onlinePlayer = Bukkit.getPlayerExact(account.username());
        if (onlinePlayer != null) {
            meta.setOwningPlayer(onlinePlayer);
        } else {
            meta.setPlayerProfile(Bukkit.createProfile(account.username()));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(online
                ? Component.text("Online", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)
                : Component.text("Offline", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        if (alreadyOnTeam) {
            lore.add(Component.text("Already on " + targetTeam.displayName(), NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (currentTeam.isPresent()) {
            lore.add(Component.text("On " + currentTeam.get().displayName() + " — click to move", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("Unassigned", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.empty());
        lore.add(Component.text("Click to assign to " + targetTeam.displayName(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        head.setItemMeta(meta);
        keys.tag(head, TeamAdminGuiKeys.Action.ASSIGN_PICK, targetTeam.id(), account.id());
        return head;
    }

    private List<Account> sortedAssignableAccounts() {
        Set<String> onlineNames = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlineNames.add(player.getName().toLowerCase(Locale.ROOT));
        }
        List<Account> accounts = new ArrayList<>(teams.listAccounts());
        accounts.sort(Comparator
                .comparing((Account account) -> !onlineNames.contains(account.username().toLowerCase(Locale.ROOT)))
                .thenComparing(account -> account.username().toLowerCase(Locale.ROOT)));
        return accounts;
    }

    private ItemStack memberHead(Team team, TeamMember member) {
        boolean isCaptain = team.captainAccountId() != null
                && team.captainAccountId().equals(member.accountId());
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        Component name = Component.text(member.username(), isCaptain ? NamedTextColor.GOLD : NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false);
        if (isCaptain) {
            name = name.decorate(TextDecoration.BOLD);
        }
        meta.displayName(name);
        Player online = Bukkit.getPlayerExact(member.username());
        if (online != null) {
            meta.setOwningPlayer(online);
        } else {
            meta.setPlayerProfile(Bukkit.createProfile(member.username()));
        }
        meta.lore(List.of(
                isCaptain
                        ? Component.text("Captain", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)
                        : Component.text("Member", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Left-click: remove from team", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: set as captain", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        head.setItemMeta(meta);
        // Tag as remove by default; listener distinguishes click type.
        keys.tag(head, TeamAdminGuiKeys.Action.REMOVE_MEMBER, team.id(), member.accountId());
        return head;
    }

    private static void fill(Inventory inventory, Material pane) {
        ItemStack filler = named(pane, Component.text(" "), List.of());
        ItemMeta meta = filler.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        filler.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler.clone());
        }
    }

    private static ItemStack named(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        List<Component> cleaned = new ArrayList<>();
        for (Component line : lore) {
            if (line != null && !Component.empty().equals(line)) {
                cleaned.add(line);
            }
        }
        if (!cleaned.isEmpty()) {
            meta.lore(cleaned);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack infoItem(Material material, Component name, List<Component> lore) {
        return named(material, name, lore);
    }

    private static List<Component> wrapLore(String text, NamedTextColor color) {
        List<Component> lines = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            if (remaining.length() <= 40) {
                lines.add(Component.text(remaining, color).decoration(TextDecoration.ITALIC, false));
                break;
            }
            int breakAt = remaining.lastIndexOf(' ', 40);
            if (breakAt <= 0) {
                breakAt = 40;
            }
            lines.add(Component.text(remaining.substring(0, breakAt), color)
                    .decoration(TextDecoration.ITALIC, false));
            remaining = remaining.substring(breakAt).stripLeading();
        }
        return lines;
    }

    static Material concreteFor(TeamColor color) {
        return switch (color) {
            case BLUE -> Material.BLUE_CONCRETE;
            case RED -> Material.RED_CONCRETE;
            case YELLOW -> Material.YELLOW_CONCRETE;
            case GREEN -> Material.LIME_CONCRETE;
            case AQUA -> Material.CYAN_CONCRETE;
            case GOLD -> Material.ORANGE_CONCRETE;
            case LIGHT_PURPLE -> Material.MAGENTA_CONCRETE;
            case WHITE -> Material.WHITE_CONCRETE;
            case GRAY -> Material.GRAY_CONCRETE;
        };
    }

    private static Material paneFor(TeamColor color) {
        return switch (color) {
            case BLUE -> Material.BLUE_STAINED_GLASS_PANE;
            case RED -> Material.RED_STAINED_GLASS_PANE;
            case YELLOW -> Material.YELLOW_STAINED_GLASS_PANE;
            case GREEN -> Material.LIME_STAINED_GLASS_PANE;
            case AQUA -> Material.CYAN_STAINED_GLASS_PANE;
            case GOLD -> Material.ORANGE_STAINED_GLASS_PANE;
            case LIGHT_PURPLE -> Material.MAGENTA_STAINED_GLASS_PANE;
            case WHITE -> Material.WHITE_STAINED_GLASS_PANE;
            case GRAY -> Material.GRAY_STAINED_GLASS_PANE;
        };
    }
}
