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
import io.papermc.paper.dialog.Dialog;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Teams Admin dialog console, hotbar shortcut, and tool lifecycle.
 *
 * <p>All admin screens are Paper dialogs. Chest menus are not used.
 */
public final class TeamAdminListener implements Listener {

    private final Plugin plugin;
    private final TeamService teams;
    private final TeamAdminDialogFactory dialogs;
    private final TeamAdminSession sessions;
    private final TeamAdminTool tool;
    private final TeamAdminAuthorizer authorizer;

    public TeamAdminListener(
            Plugin plugin,
            TeamService teams,
            TeamAdminDialogFactory dialogs,
            TeamAdminSession sessions,
            TeamAdminTool tool,
            TeamAdminAuthorizer authorizer
    ) {
        this.plugin = plugin;
        this.teams = teams;
        this.dialogs = dialogs;
        this.sessions = sessions;
        this.tool = tool;
        this.authorizer = authorizer;
    }

    public void openHub(Player player, @Nullable String statusMessage) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_OPEN_UI)) {
            player.sendMessage(Component.text("You are not allowed to manage teams.", NamedTextColor.RED));
            return;
        }
        showDialogNow(player, dialogs.hubDialog(statusMessage));
    }

    public void openTeam(Player player, UUID teamId, @Nullable String statusMessage) {
        Optional<Team> team = teams.getTeam(teamId);
        if (team.isEmpty()) {
            sessions.clear(player.getUniqueId());
            openHub(player, "That team no longer exists.");
            return;
        }
        sessions.selectTeam(player.getUniqueId(), teamId);
        showDialogNow(player, dialogs.teamDialog(team.get(), statusMessage));
    }

    private void showDialogNow(Player player, Dialog dialog) {
        player.closeDialog();
        player.showDialog(dialog);
    }

    private void showDialogNextTick(Player player, java.util.function.Supplier<Dialog> dialog) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            showDialogNow(player, dialog.get());
        });
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType().isAir()) {
            item = player.getInventory().getItemInMainHand();
        }
        if (!tool.isTool(item)) {
            return;
        }
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (!player.isOp() || !isAuthorized(player, TeamAdminAction.TEAM_OPEN_UI)) {
            tool.stripAll(player.getInventory());
            player.sendMessage(Component.text("You are not allowed to manage teams.", NamedTextColor.RED));
            return;
        }
        // showDialog during PlayerInteractEvent is dropped by the client; open next tick.
        showDialogNextTick(player, () -> dialogs.hubDialog(null));
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

        if (identifier.equals(TeamAdminDialogKeys.CLOSE)) {
            sessions.clear(player.getUniqueId());
            player.closeDialog();
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.OPEN_HUB)) {
            openHub(player, null);
            return;
        }
        Optional<UUID> openTeamId = TeamAdminDialogKeys.parseOpenTeam(identifier);
        if (openTeamId.isPresent()) {
            openTeam(player, openTeamId.get(), null);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.OPEN_CREATE)) {
            if (!isAuthorized(player, TeamAdminAction.TEAM_CREATE)) {
                deny(player);
                return;
            }
            player.showDialog(dialogs.createTeamDialog(null));
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.CREATE_SUBMIT)) {
            handleCreate(player, view);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.OPEN_WARNINGS)) {
            if (!isAuthorized(player, TeamAdminAction.TEAM_VIEW)) {
                deny(player);
                return;
            }
            player.showDialog(dialogs.warningsDialog());
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.OPEN_EDIT)) {
            handleOpenEdit(player);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.EDIT_SUBMIT)) {
            handleEdit(player, view);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.OPEN_ASSIGN)) {
            handleOpenAssign(player);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.ASSIGN_SUBMIT)) {
            handleAssign(player, view);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.OPEN_SET_CAPTAIN)) {
            handleOpenSetCaptain(player);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.SET_CAPTAIN_SUBMIT)) {
            handleSetCaptainSubmit(player, view);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.CLEAR_CAPTAIN)) {
            handleClearCaptain(player);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.OPEN_REMOVE_MEMBER)) {
            handleOpenRemoveMember(player);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.REMOVE_MEMBER_SUBMIT)) {
            handleRemoveMemberSubmit(player, view);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.OPEN_DELETE)) {
            handleOpenDelete(player);
            return;
        }
        if (identifier.equals(TeamAdminDialogKeys.DELETE_CONFIRM)) {
            handleDelete(player);
        }
    }

    private void handleOpenEdit(Player player) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_EDIT)) {
            deny(player);
            return;
        }
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        player.showDialog(dialogs.editTeamDialog(selected, null));
    }

    private void handleOpenAssign(Player player) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_ASSIGN)) {
            deny(player);
            return;
        }
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        player.showDialog(dialogs.assignDialog(selected, null));
    }

    private void handleOpenSetCaptain(Player player) {
        if (!isAuthorized(player, TeamAdminAction.CAPTAIN_SET)) {
            deny(player);
            return;
        }
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        player.showDialog(dialogs.setCaptainDialog(selected, null));
    }

    private void handleOpenRemoveMember(Player player) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_UNASSIGN)) {
            deny(player);
            return;
        }
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        player.showDialog(dialogs.removeMemberDialog(selected, null));
    }

    private void handleOpenDelete(Player player) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_DELETE)) {
            deny(player);
            return;
        }
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        player.showDialog(dialogs.deleteConfirmDialog(selected));
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

    private void handleSetCaptainSubmit(Player player, @Nullable DialogResponseView view) {
        if (!isAuthorized(player, TeamAdminAction.CAPTAIN_SET)) {
            deny(player);
            return;
        }
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        String username = text(view, TeamAdminDialogKeys.INPUT_MEMBER);
        var account = teams.findAccountByUsername(username);
        if (account.isEmpty()) {
            player.showDialog(dialogs.setCaptainDialog(selected, "No registered account named '" + username + "'."));
            return;
        }
        TeamMutationResult result = teams.setCaptain(selected.id(), account.get().id());
        if (!result.success()) {
            player.showDialog(dialogs.setCaptainDialog(selected, result.error()));
            return;
        }
        openTeam(player, selected.id(), formatFeedback(result));
    }

    private void handleRemoveMemberSubmit(Player player, @Nullable DialogResponseView view) {
        if (!isAuthorized(player, TeamAdminAction.TEAM_UNASSIGN)) {
            deny(player);
            return;
        }
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        String username = text(view, TeamAdminDialogKeys.INPUT_MEMBER);
        var account = teams.findAccountByUsername(username);
        if (account.isEmpty()) {
            player.showDialog(dialogs.removeMemberDialog(selected, "No registered account named '" + username + "'."));
            return;
        }
        TeamMutationResult result = teams.removeMember(account.get().id());
        if (!result.success()) {
            player.showDialog(dialogs.removeMemberDialog(selected, result.error()));
            return;
        }
        openTeam(player, selected.id(), formatFeedback(result));
    }

    private void handleClearCaptain(Player player) {
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        if (!isAuthorized(player, TeamAdminAction.CAPTAIN_CLEAR)) {
            deny(player);
            return;
        }
        TeamMutationResult result = teams.clearCaptain(selected.id());
        openTeam(player, selected.id(), formatFeedback(result));
    }

    private void handleDelete(Player player) {
        Team selected = requireSelectedTeam(player).orElse(null);
        if (selected == null) {
            openHub(player, "Select a team first.");
            return;
        }
        if (!isAuthorized(player, TeamAdminAction.TEAM_DELETE)) {
            deny(player);
            return;
        }
        UUID teamId = selected.id();
        TeamMutationResult result = teams.deleteTeam(teamId);
        sessions.clearTeam(teamId);
        sessions.clear(player.getUniqueId());
        openHub(player, formatFeedback(result));
    }

    private Optional<Team> requireSelectedTeam(Player player) {
        return sessions.selectedTeam(player.getUniqueId()).flatMap(teams::getTeam);
    }

    private boolean isAuthorized(Player player, TeamAdminAction action) {
        return authorizer.isAllowed(playerSource(player), action);
    }

    private static void deny(Player player) {
        player.sendMessage(Component.text("You are not allowed to do that.", NamedTextColor.RED));
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
