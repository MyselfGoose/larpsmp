package com.larpsmp.moneyevent.team;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.command.MessageKind;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Teams Admin chest GUI, typed dialog forms, hotbar shortcut, and tool lifecycle.
 */
public final class TeamAdminListener implements Listener {

    private final Plugin plugin;
    private final TeamService teams;
    private final TeamAdminMenus menus;
    private final TeamAdminDialogFactory dialogs;
    private final TeamAdminSession sessions;
    private final TeamAdminGuiKeys guiKeys;
    private final TeamAdminTool tool;
    private final TeamAdminAuthorizer authorizer;

    public TeamAdminListener(
            Plugin plugin,
            TeamService teams,
            TeamAdminMenus menus,
            TeamAdminDialogFactory dialogs,
            TeamAdminSession sessions,
            TeamAdminGuiKeys guiKeys,
            TeamAdminTool tool,
            TeamAdminAuthorizer authorizer
    ) {
        this.plugin = plugin;
        this.teams = teams;
        this.menus = menus;
        this.dialogs = dialogs;
        this.sessions = sessions;
        this.guiKeys = guiKeys;
        this.tool = tool;
        this.authorizer = authorizer;
    }

    public void openHub(Player player, @Nullable String statusMessage) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_OPEN_UI)) {
            player.sendMessage(Component.text("You are not allowed to manage teams.", NamedTextColor.RED));
            return;
        }
        player.closeDialog();
        player.openInventory(menus.hub(statusMessage));
        if (statusMessage != null && !statusMessage.isBlank()) {
            player.sendMessage(Component.text(statusMessage, NamedTextColor.YELLOW));
        }
    }

    public void openTeam(Player player, UUID teamId, @Nullable String statusMessage) {
        Optional<Team> team = teams.getTeam(teamId);
        if (team.isEmpty()) {
            sessions.clear(player.getUniqueId());
            openHub(player, "That team no longer exists.");
            return;
        }
        sessions.selectTeam(player.getUniqueId(), teamId);
        player.closeDialog();
        player.openInventory(menus.team(team.get(), statusMessage));
        if (statusMessage != null && !statusMessage.isBlank()) {
            player.sendMessage(Component.text(statusMessage, NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!player.isOp() || !isAuthorized(player, TeamAdminAction.TEAM_OPEN_UI)) {
                tool.stripAll(player.getInventory());
                return;
            }
            boolean placed = tool.grantPreferringSlot8(player.getInventory());
            if (!placed) {
                player.sendMessage(Component.text(
                        "Teams Admin tool could not be placed (inventory full). Use /larp teams.",
                        NamedTextColor.GOLD));
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sessions.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (!tool.isTool(item)) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.isOp() || !isAuthorized(player, TeamAdminAction.TEAM_OPEN_UI)) {
            tool.stripAll(player.getInventory());
            player.sendMessage(Component.text("You are not allowed to manage teams.", NamedTextColor.RED));
            return;
        }
        openHub(player, null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (tool.isTool(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(tool::isTool);
        tool.stripAll(event.getEntity().getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof TeamAdminMenu) {
            event.setCancelled(true);
            if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            TeamAdminGuiKeys.Action action = guiKeys.actionOf(clicked);
            if (action == null) {
                return;
            }
            handleGuiAction(player, action, clicked, event.getClick());
            return;
        }

        // Protect the hotbar tool from being moved into other inventories.
        boolean playerOnlyView = top.equals(player.getInventory());
        if (playerOnlyView) {
            return;
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        if (tool.isTool(cursor) && event.getClickedInventory() != null
                && !event.getClickedInventory().equals(player.getInventory())) {
            event.setCancelled(true);
            return;
        }
        if (tool.isTool(current) && event.isShiftClick()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof TeamAdminMenu) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().equals(player.getInventory())) {
            return;
        }
        if (!tool.isTool(event.getOldCursor())
                && event.getNewItems().values().stream().noneMatch(tool::isTool)) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean intoTop = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
        if (intoTop) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent event) {
        Key identifier = event.getIdentifier();
        if (!TeamAdminDialogKeys.isTeamAdminKey(identifier)) {
            return;
        }
        if (!(event.getCommonConnection() instanceof PlayerGameConnection gameConnection)) {
            return;
        }
        Player player = gameConnection.getPlayer();
        if (!isAuthorized(player, TeamAdminAction.TEAM_OPEN_UI)) {
            player.sendMessage(Component.text("You are not allowed to manage teams.", NamedTextColor.RED));
            player.closeDialog();
            return;
        }

        DialogResponseView view = event.getDialogResponseView();

        if (identifier.equals(TeamAdminDialogKeys.OPEN_HUB)
                || identifier.equals(TeamAdminDialogKeys.CLOSE)) {
            openHub(player, null);
            return;
        }
        Optional<UUID> openTeamId = TeamAdminDialogKeys.parseOpenTeam(identifier);
        if (openTeamId.isPresent()) {
            openTeam(player, openTeamId.get(), null);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.CREATE_SUBMIT)) {
            handleCreate(player, view);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.EDIT_SUBMIT)) {
            handleEdit(player, view);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.ASSIGN_SUBMIT)) {
            handleAssign(player, view);
        }
    }

    private void handleGuiAction(
            Player player,
            TeamAdminGuiKeys.Action action,
            ItemStack clicked,
            ClickType click
    ) {
        switch (action) {
            case OPEN_TEAM -> {
                UUID teamId = guiKeys.teamIdOf(clicked);
                if (teamId != null) {
                    openTeam(player, teamId, null);
                }
            }
            case CREATE -> {
                if (!isAuthorized(player, TeamAdminAction.TEAM_CREATE)) {
                    deny(player);
                    return;
                }
                player.closeInventory();
                player.showDialog(dialogs.createTeamDialog(null));
            }
            case WARNINGS -> {
                if (!isAuthorized(player, TeamAdminAction.TEAM_VIEW)) {
                    deny(player);
                    return;
                }
                player.openInventory(menus.warnings());
            }
            case WARNINGS_BACK, BACK_HUB, CLOSE -> {
                if (action == TeamAdminGuiKeys.Action.CLOSE) {
                    player.closeInventory();
                    sessions.clear(player.getUniqueId());
                } else {
                    openHub(player, null);
                }
            }
            case ASSIGN -> {
                Team team = requireTeam(player, guiKeys.teamIdOf(clicked));
                if (team == null || !isAuthorized(player, TeamAdminAction.TEAM_ASSIGN)) {
                    deny(player);
                    return;
                }
                sessions.selectTeam(player.getUniqueId(), team.id());
                player.openInventory(menus.assign(team, 0));
            }
            case ASSIGN_PICK -> {
                UUID teamId = guiKeys.teamIdOf(clicked);
                UUID accountId = guiKeys.accountIdOf(clicked);
                if (teamId == null || accountId == null || !isAuthorized(player, TeamAdminAction.TEAM_ASSIGN)) {
                    deny(player);
                    return;
                }
                TeamMutationResult result = teams.assignMember(accountId, teamId);
                openTeam(player, teamId, formatFeedback(result));
            }
            case ASSIGN_PAGE_PREV, ASSIGN_PAGE_NEXT -> {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof TeamAdminMenu menu)
                        || menu.teamId() == null) {
                    return;
                }
                Team team = requireTeam(player, menu.teamId());
                if (team == null) {
                    return;
                }
                int nextPage = action == TeamAdminGuiKeys.Action.ASSIGN_PAGE_PREV
                        ? menu.page() - 1
                        : menu.page() + 1;
                player.openInventory(menus.assign(team, nextPage));
            }
            case ASSIGN_TYPE -> {
                Team team = requireTeam(player, guiKeys.teamIdOf(clicked));
                if (team == null || !isAuthorized(player, TeamAdminAction.TEAM_ASSIGN)) {
                    deny(player);
                    return;
                }
                sessions.selectTeam(player.getUniqueId(), team.id());
                player.closeInventory();
                player.showDialog(dialogs.assignDialog(team, null));
            }
            case EDIT -> {
                Team team = requireTeam(player, guiKeys.teamIdOf(clicked));
                if (team == null || !isAuthorized(player, TeamAdminAction.TEAM_EDIT)) {
                    deny(player);
                    return;
                }
                sessions.selectTeam(player.getUniqueId(), team.id());
                player.closeInventory();
                player.showDialog(dialogs.editTeamDialog(team, null));
            }
            case DELETE -> {
                Team team = requireTeam(player, guiKeys.teamIdOf(clicked));
                if (team == null || !isAuthorized(player, TeamAdminAction.TEAM_DELETE)) {
                    deny(player);
                    return;
                }
                sessions.selectTeam(player.getUniqueId(), team.id());
                player.openInventory(menus.deleteConfirm(team));
            }
            case DELETE_CONFIRM -> handleDelete(player, guiKeys.teamIdOf(clicked));
            case DELETE_CANCEL, BACK_TEAM -> {
                UUID teamId = guiKeys.teamIdOf(clicked);
                if (teamId != null) {
                    openTeam(player, teamId, null);
                } else {
                    openHub(player, null);
                }
            }
            case REMOVE_MEMBER -> {
                UUID accountId = guiKeys.accountIdOf(clicked);
                UUID teamId = guiKeys.teamIdOf(clicked);
                if (accountId == null || teamId == null) {
                    return;
                }
                if (click.isRightClick()) {
                    handleSetCaptain(player, teamId, accountId);
                } else {
                    handleRemoveMember(player, teamId, accountId);
                }
            }
            case SET_CAPTAIN -> {
                UUID accountId = guiKeys.accountIdOf(clicked);
                UUID teamId = guiKeys.teamIdOf(clicked);
                if (accountId != null && teamId != null) {
                    handleSetCaptain(player, teamId, accountId);
                }
            }
            case CLEAR_CAPTAIN -> handleClearCaptain(player, guiKeys.teamIdOf(clicked));
        }
    }

    private void handleCreate(Player player, @Nullable DialogResponseView view) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_CREATE)) {
            deny(player);
            return;
        }
        String name = text(view, TeamAdminDialogKeys.INPUT_NAME);
        TeamColor color = TeamColor.fromKey(text(view, TeamAdminDialogKeys.INPUT_COLOR)).orElse(null);
        if (color == null) {
            player.showDialog(dialogs.createTeamDialog("Choose a valid color."));
            return;
        }
        TeamMutationResult result = teams.createTeam(name, color);
        if (!result.success()) {
            player.showDialog(dialogs.createTeamDialog(result.error()));
            return;
        }
        if (result.team() != null) {
            openTeam(player, result.team().id(), formatFeedback(result));
        } else {
            openHub(player, formatFeedback(result));
        }
    }

    private void handleEdit(Player player, @Nullable DialogResponseView view) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_EDIT)) {
            deny(player);
            return;
        }
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        String name = text(view, TeamAdminDialogKeys.INPUT_NAME);
        TeamColor color = TeamColor.fromKey(text(view, TeamAdminDialogKeys.INPUT_COLOR)).orElse(null);
        TeamMutationResult result = teams.updateTeam(selected.id(), name, color);
        if (!result.success()) {
            player.showDialog(dialogs.editTeamDialog(selected, result.error()));
            return;
        }
        Team updated = result.team() == null ? selected : result.team();
        openTeam(player, updated.id(), formatFeedback(result));
    }

    private void handleAssign(Player player, @Nullable DialogResponseView view) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_ASSIGN)) {
            deny(player);
            return;
        }
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        String username = text(view, TeamAdminDialogKeys.INPUT_USERNAME);
        var account = teams.findAccountByUsername(username);
        if (account.isEmpty()) {
            player.showDialog(dialogs.assignDialog(selected, "No registered account named '" + username + "'."));
            return;
        }
        TeamMutationResult result = teams.assignMember(account.get().id(), selected.id());
        if (!result.success()) {
            player.showDialog(dialogs.assignDialog(selected, result.error()));
            return;
        }
        openTeam(player, selected.id(), formatFeedback(result));
    }

    private void handleRemoveMember(Player player, UUID teamId, UUID accountId) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_UNASSIGN)) {
            deny(player);
            return;
        }
        TeamMutationResult result = teams.removeMember(accountId);
        openTeam(player, teamId, formatFeedback(result));
    }

    private void handleSetCaptain(Player player, UUID teamId, UUID accountId) {
        if (!isAuthorized(player, TeamAdminAction.CAPTAIN_SET)) {
            deny(player);
            return;
        }
        TeamMutationResult result = teams.setCaptain(teamId, accountId);
        openTeam(player, teamId, formatFeedback(result));
    }

    private void handleClearCaptain(Player player, @Nullable UUID teamId) {
        if (teamId == null || !isAuthorized(player, TeamAdminAction.CAPTAIN_CLEAR)) {
            deny(player);
            return;
        }
        TeamMutationResult result = teams.clearCaptain(teamId);
        openTeam(player, teamId, formatFeedback(result));
    }

    private void handleDelete(Player player, @Nullable UUID teamId) {
        if (teamId == null || !isAuthorized(player, TeamAdminAction.TEAM_DELETE)) {
            deny(player);
            return;
        }
        TeamMutationResult result = teams.deleteTeam(teamId);
        sessions.clearTeam(teamId);
        sessions.clear(player.getUniqueId());
        openHub(player, formatFeedback(result));
    }

    private @Nullable Team requireTeam(Player player, @Nullable UUID teamId) {
        if (teamId == null) {
            return null;
        }
        Optional<Team> team = teams.getTeam(teamId);
        if (team.isEmpty()) {
            openHub(player, "That team no longer exists.");
            return null;
        }
        return team.get();
    }

    private Optional<Team> requireSelectedTeam(Player player) {
        return sessions.selectedTeam(player.getUniqueId()).flatMap(teams::getTeam);
    }

    private boolean isAuthorized(Player player, TeamAdminAction action) {
        return authorizer.isAllowed(playerSource(player), action);
    }

    private static void deny(Player player) {
        player.sendMessage(Component.text("You are not allowed to do that.", NamedTextColor.RED));
        player.closeInventory();
        player.closeDialog();
    }

    private static String text(@Nullable DialogResponseView view, String key) {
        if (view == null) {
            return "";
        }
        String value = view.getText(key);
        return value == null ? "" : value.trim();
    }

    private static String formatFeedback(TeamMutationResult result) {
        if (!result.success()) {
            return result.error() == null ? "Team operation failed." : result.error();
        }
        return result.allFeedback().stream().collect(Collectors.joining(" "));
    }

    private static CommandSource playerSource(Player player) {
        return new CommandSource() {
            @Override
            public Optional<UUID> playerId() {
                return Optional.of(player.getUniqueId());
            }

            @Override
            public String name() {
                return player.getName();
            }

            @Override
            public boolean isConsole() {
                return false;
            }

            @Override
            public boolean isOperator() {
                return player.isOp();
            }

            @Override
            public void send(String message, MessageKind kind) {
                player.sendMessage(Component.text(message));
            }
        };
    }
}
