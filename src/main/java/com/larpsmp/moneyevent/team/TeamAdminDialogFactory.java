package com.larpsmp.moneyevent.team;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the Teams Admin experience as compact native Paper dialogs.
 *
 * <p>Layout rules: short body text, 2-column actions at 150px, Close/Back as
 * {@code exitAction}, and {@link DialogBase.DialogAfterAction#NONE} so the
 * server can open the next screen immediately with no client wait overlay.
 */
public final class TeamAdminDialogFactory {

    private static final TextColor ACCENT = TextColor.color(0xE0B040);
    private static final TextColor CONFIRM = TextColor.color(0x5FAF5F);
    private static final TextColor DANGER = TextColor.color(0xC07070);
    private static final TextColor SECONDARY = TextColor.color(0xA0A0A0);
    private static final TextColor MUTED = TextColor.color(0x888888);
    private static final int FIELD_WIDTH = 280;
    private static final int BUTTON_WIDTH = 150;
    private static final int BODY_WIDTH = 300;

    private final TeamService teams;

    public TeamAdminDialogFactory(TeamService teams) {
        this.teams = teams;
    }

    public Dialog hubDialog(@Nullable String statusMessage) {
        List<Team> teamList = teams.listTeams();
        List<String> warnings = teams.rosterWarnings();

        List<DialogBody> body = new ArrayList<>();
        if (statusMessage != null && !statusMessage.isBlank()) {
            body.add(plain(statusMessage, NamedTextColor.YELLOW));
        } else if (warnings.isEmpty()) {
            body.add(plain("Select a team to manage. Roster looks healthy.", NamedTextColor.GRAY));
        } else {
            body.add(plain(
                    "Select a team. " + warnings.size() + " setup warning(s).",
                    NamedTextColor.GOLD));
        }
        if (teamList.isEmpty()) {
            body.add(plain("No teams yet — create one below.", NamedTextColor.DARK_GRAY));
        }

        List<ActionButton> actions = new ArrayList<>();
        for (Team team : teamList) {
            int members = teams.membersOf(team.id()).size();
            String captain = teams.captainUsername(team).orElse("no captain");
            actions.add(ActionButton.builder(Component.text(
                            team.displayName() + " (" + members + "/"
                                    + TeamService.EXPECTED_ROSTER_SIZE + ")",
                            TextColor.color(team.color().rgb()))
                    .decorate(TextDecoration.BOLD))
                    .width(BUTTON_WIDTH)
                    .tooltip(Component.text(captain + " · click to open"))
                    .action(DialogAction.customClick(TeamAdminDialogKeys.openTeam(team.id()), null))
                    .build());
        }
        actions.add(ActionButton.builder(Component.text("Create Team", CONFIRM))
                .width(BUTTON_WIDTH)
                .tooltip(Component.text("Add a new competing team"))
                .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_CREATE, null))
                .build());
        actions.add(ActionButton.builder(Component.text("Tiers", NamedTextColor.AQUA))
                .width(BUTTON_WIDTH)
                .tooltip(Component.text("Assign Tier 1–4 to rostered competitors"))
                .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_TIERS, null))
                .build());
        actions.add(ActionButton.builder(Component.text(
                        warnings.isEmpty() ? "Warnings" : "Warnings (" + warnings.size() + ")",
                        NamedTextColor.GOLD))
                .width(BUTTON_WIDTH)
                .tooltip(Component.text("Review roster setup warnings"))
                .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_WARNINGS, null))
                .build());

        return Dialog.create(factory -> factory.empty()
                .base(base("Teams Admin", ACCENT, body))
                .type(DialogType.multiAction(actions)
                        .columns(2)
                        .exitAction(closeButton())
                        .build()));
    }

    public Dialog teamDialog(Team team, @Nullable String statusMessage) {
        List<TeamMember> members = teams.membersOf(team.id());
        String captain = teams.captainUsername(team).orElse("None");
        TextColor teamColor = TextColor.color(team.color().rgb());

        List<DialogBody> body = new ArrayList<>();
        body.add(plain(
                members.size() + "/" + TeamService.EXPECTED_ROSTER_SIZE
                        + " members · Captain: " + captain,
                NamedTextColor.GRAY));
        if (members.isEmpty()) {
            body.add(plain("Roster empty — assign a player.", NamedTextColor.DARK_GRAY));
        } else {
            for (TeamMember member : members) {
                boolean isCaptain = team.captainAccountId() != null
                        && team.captainAccountId().equals(member.accountId());
                Optional<PlayerTier> tier = member.playerTier();
                String label = (isCaptain ? "★ " : "") + member.username()
                        + " · " + tier.map(PlayerTier::shortLabel).orElse("no tier");
                TextColor color = tier.map(value -> TextColor.color(value.rgb()))
                        .orElse(NamedTextColor.DARK_GRAY);
                body.add(plain(label, color));
            }
        }
        appendStatus(body, statusMessage);

        List<ActionButton> actions = List.of(
                ActionButton.builder(Component.text("Assign", CONFIRM))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Add or move a registered account"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_ASSIGN, null))
                        .build(),
                ActionButton.builder(Component.text("Edit", NamedTextColor.AQUA))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Rename or change color"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_EDIT, null))
                        .build(),
                ActionButton.builder(Component.text("Set Captain", NamedTextColor.GOLD))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Choose captain from members"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_SET_CAPTAIN, null))
                        .build(),
                ActionButton.builder(Component.text("Set Tier", NamedTextColor.LIGHT_PURPLE))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Assign Tier 1–4 to a member"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_SET_TIER_PICKER, null))
                        .build(),
                ActionButton.builder(Component.text("Clear Captain", NamedTextColor.YELLOW))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Remove the current captain"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.CLEAR_CAPTAIN, null))
                        .build(),
                ActionButton.builder(Component.text("Remove", DANGER))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Remove a player from this roster"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_REMOVE_MEMBER, null))
                        .build(),
                ActionButton.builder(Component.text("Delete", NamedTextColor.RED))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Permanently delete this team"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_DELETE, null))
                        .build()
        );

        return Dialog.create(factory -> factory.empty()
                .base(base(team.displayName(), teamColor, body))
                .type(DialogType.multiAction(actions)
                        .columns(2)
                        .exitAction(backToHubButton())
                        .build()));
    }

    public Dialog tiersBoardDialog(@Nullable String statusMessage) {
        List<JdbcTeamRepository.RosteredCompetitor> competitors = teams.listRosteredCompetitors();
        long unset = competitors.stream().filter(row -> row.tier() == null).count();
        List<String> warnings = teams.rosterWarnings().stream()
                .filter(warning -> warning.contains("Tier") || warning.contains("tier"))
                .toList();

        List<DialogBody> body = new ArrayList<>();
        if (statusMessage != null && !statusMessage.isBlank()) {
            body.add(plain(statusMessage, NamedTextColor.YELLOW));
        } else if (competitors.isEmpty()) {
            body.add(plain("No rostered competitors yet — assign players to teams first.", MUTED));
        } else {
            body.add(plain(
                    competitors.size() + " competitors · " + unset + " without a tier"
                            + (warnings.isEmpty() ? "" : " · " + warnings.size() + " tier warning(s)"),
                    NamedTextColor.GRAY));
        }

        List<ActionButton> actions = new ArrayList<>();
        for (JdbcTeamRepository.RosteredCompetitor competitor : competitors) {
            Optional<PlayerTier> tier = competitor.playerTier();
            TextColor color = tier.map(value -> TextColor.color(value.rgb())).orElse(MUTED);
            String label = competitor.username() + " · " + competitor.teamDisplayName()
                    + " · " + tier.map(PlayerTier::shortLabel).orElse("unset");
            actions.add(ActionButton.builder(Component.text(label, color))
                    .width(BUTTON_WIDTH)
                    .tooltip(Component.text("Set tier for " + competitor.username()))
                    .action(DialogAction.customClick(
                            TeamAdminDialogKeys.openSetTier(competitor.accountId()), null))
                    .build());
        }

        return Dialog.create(factory -> factory.empty()
                .base(base("Competitor Tiers", ACCENT, body))
                .type(DialogType.multiAction(actions.isEmpty()
                                ? List.of(ActionButton.builder(Component.text("Refresh", SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_TIERS, null))
                                        .build())
                                : actions)
                        .columns(2)
                        .exitAction(backToHubButton())
                        .build()));
    }

    public Dialog setTierDialog(
            String username,
            UUID accountId,
            @Nullable PlayerTier current,
            @Nullable Key backKey,
            @Nullable String statusMessage
    ) {
        List<DialogBody> body = new ArrayList<>();
        body.add(plain(
                username + " · " + (current == null ? "no tier" : current.displayName()),
                current == null ? MUTED : TextColor.color(current.rgb())));
        appendStatus(body, statusMessage);

        List<ActionButton> actions = new ArrayList<>();
        for (PlayerTier tier : PlayerTier.values()) {
            boolean selected = current == tier;
            actions.add(ActionButton.builder(Component.text(
                            selected ? "● " + tier.displayName() : tier.displayName(),
                            TextColor.color(tier.rgb()))
                    .decorate(TextDecoration.BOLD))
                    .width(BUTTON_WIDTH)
                    .tooltip(Component.text("Assign " + tier.displayName()))
                    .action(DialogAction.customClick(TeamAdminDialogKeys.setTier(tier), null))
                    .build());
        }
        actions.add(ActionButton.builder(Component.text("Clear Tier", DANGER))
                .width(BUTTON_WIDTH)
                .tooltip(Component.text("Remove tier assignment"))
                .action(DialogAction.customClick(TeamAdminDialogKeys.TIER_CLEAR, null))
                .build());

        Key exit = backKey == null ? TeamAdminDialogKeys.OPEN_TIERS : backKey;
        return Dialog.create(factory -> factory.empty()
                .base(base("Set Tier · " + username, ACCENT, body))
                .type(DialogType.multiAction(actions)
                        .columns(2)
                        .exitAction(ActionButton.builder(Component.text("Back", SECONDARY))
                                .width(BUTTON_WIDTH)
                                .action(DialogAction.customClick(exit, null))
                                .build())
                        .build()));
    }

    public Dialog setTierPickerDialog(Team team, @Nullable String statusMessage) {
        List<TeamMember> members = teams.membersOf(team.id());
        List<DialogBody> body = bodyWithStatus(
                members.isEmpty()
                        ? "No members — assign someone first."
                        : "Choose a member of " + team.displayName() + " to set their tier.",
                statusMessage);

        if (members.isEmpty()) {
            return emptyPickerDialog("Set Tier", body, TeamAdminDialogKeys.openTeam(team.id()));
        }

        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            TeamMember member = members.get(i);
            Optional<PlayerTier> tier = member.playerTier();
            options.add(SingleOptionDialogInput.OptionEntry.create(
                    member.username(),
                    Component.text(
                            member.username() + " · " + tier.map(PlayerTier::shortLabel).orElse("unset"),
                            tier.map(value -> TextColor.color(value.rgb())).orElse(NamedTextColor.WHITE)),
                    i == 0
            ));
        }

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Set Tier", ACCENT))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.singleOption(
                                                TeamAdminDialogKeys.INPUT_MEMBER,
                                                Component.text("Member"),
                                                options)
                                        .width(FIELD_WIDTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text("Continue", CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(
                                                TeamAdminDialogKeys.SET_TIER_PICK_SUBMIT, null))
                                        .build()
                        ))
                        .columns(1)
                        .exitAction(backToTeamButton(team.id()))
                        .build()));
    }

    public Dialog createTeamDialog(@Nullable String statusMessage) {
        return formDialog(
                "Create Team",
                "Name and color for the new team.",
                statusMessage,
                null,
                TeamColor.BLUE,
                TeamAdminDialogKeys.CREATE_SUBMIT,
                "Create",
                TeamAdminDialogKeys.OPEN_HUB
        );
    }

    public Dialog editTeamDialog(Team team, @Nullable String statusMessage) {
        return formDialog(
                "Edit " + team.displayName(),
                "Update name or color.",
                statusMessage,
                team.displayName(),
                team.color(),
                TeamAdminDialogKeys.EDIT_SUBMIT,
                "Save",
                TeamAdminDialogKeys.openTeam(team.id())
        );
    }

    public Dialog assignDialog(Team team, @Nullable String statusMessage) {
        List<String> usernames = teams.listUsernames();
        List<DialogBody> body = bodyWithStatus(
                usernames.isEmpty()
                        ? "No registered accounts yet."
                        : "Assign a player to " + team.displayName() + ".",
                statusMessage);

        if (usernames.isEmpty()) {
            return emptyPickerDialog("Assign Player", body, TeamAdminDialogKeys.openTeam(team.id()));
        }

        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (int i = 0; i < usernames.size(); i++) {
            String username = usernames.get(i);
            AssignLabel label = labelForAssignOption(team, username);
            options.add(SingleOptionDialogInput.OptionEntry.create(
                    username,
                    Component.text(label.display(), label.color()),
                    i == 0
            ));
        }

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Assign Player", ACCENT))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.singleOption(
                                                TeamAdminDialogKeys.INPUT_USERNAME,
                                                Component.text("Player"),
                                                options)
                                        .width(FIELD_WIDTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text("Assign", CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(TeamAdminDialogKeys.ASSIGN_SUBMIT, null))
                                        .build()
                        ))
                        .columns(1)
                        .exitAction(backToTeamButton(team.id()))
                        .build()));
    }

    public Dialog setCaptainDialog(Team team, @Nullable String statusMessage) {
        List<TeamMember> members = teams.membersOf(team.id());
        List<DialogBody> body = bodyWithStatus(
                members.isEmpty()
                        ? "No members — assign someone first."
                        : "Choose captain for " + team.displayName() + ".",
                statusMessage);

        if (members.isEmpty()) {
            return emptyPickerDialog("Set Captain", body, TeamAdminDialogKeys.openTeam(team.id()));
        }

        List<SingleOptionDialogInput.OptionEntry> options = memberOptions(team, members);

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Set Captain", ACCENT))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.singleOption(
                                                TeamAdminDialogKeys.INPUT_MEMBER,
                                                Component.text("Member"),
                                                options)
                                        .width(FIELD_WIDTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text("Set Captain", CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(TeamAdminDialogKeys.SET_CAPTAIN_SUBMIT, null))
                                        .build()
                        ))
                        .columns(1)
                        .exitAction(backToTeamButton(team.id()))
                        .build()));
    }

    public Dialog removeMemberDialog(Team team, @Nullable String statusMessage) {
        List<TeamMember> members = teams.membersOf(team.id());
        List<DialogBody> body = bodyWithStatus(
                members.isEmpty()
                        ? "No members to remove."
                        : "Remove a member from " + team.displayName() + ".",
                statusMessage);

        if (members.isEmpty()) {
            return emptyPickerDialog("Remove Member", body, TeamAdminDialogKeys.openTeam(team.id()));
        }

        List<SingleOptionDialogInput.OptionEntry> options = memberOptions(team, members);

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Remove Member", DANGER))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.singleOption(
                                                TeamAdminDialogKeys.INPUT_MEMBER,
                                                Component.text("Member"),
                                                options)
                                        .width(FIELD_WIDTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text("Remove", NamedTextColor.RED))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(TeamAdminDialogKeys.REMOVE_MEMBER_SUBMIT, null))
                                        .build()
                        ))
                        .columns(1)
                        .exitAction(backToTeamButton(team.id()))
                        .build()));
    }

    public Dialog deleteConfirmDialog(Team team) {
        int members = teams.membersOf(team.id()).size();
        List<DialogBody> body = List.of(
                plain("Delete " + team.displayName() + "?", NamedTextColor.RED),
                plain("Removes " + members + " membership(s). Cannot be undone.", NamedTextColor.GRAY)
        );

        return Dialog.create(factory -> factory.empty()
                .base(base("Delete Team", NamedTextColor.RED, body))
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("Confirm Delete", NamedTextColor.RED))
                                .width(BUTTON_WIDTH)
                                .action(DialogAction.customClick(TeamAdminDialogKeys.DELETE_CONFIRM, null))
                                .build(),
                        ActionButton.builder(Component.text("Cancel", SECONDARY))
                                .width(BUTTON_WIDTH)
                                .action(DialogAction.customClick(TeamAdminDialogKeys.openTeam(team.id()), null))
                                .build()
                )));
    }

    public Dialog warningsDialog() {
        List<String> warnings = teams.rosterWarnings();
        List<DialogBody> body = new ArrayList<>();
        if (warnings.isEmpty()) {
            body.add(plain("All clear — no roster warnings.", NamedTextColor.GREEN));
        } else {
            int shown = Math.min(warnings.size(), 8);
            for (int i = 0; i < shown; i++) {
                body.add(plain("• " + warnings.get(i), NamedTextColor.GRAY));
            }
            if (warnings.size() > shown) {
                body.add(plain("…and " + (warnings.size() - shown) + " more.", MUTED));
            }
        }

        return Dialog.create(factory -> factory.empty()
                .base(base("Roster Warnings", ACCENT, body))
                .type(DialogType.notice(backToHubButton())));
    }

    private Dialog formDialog(
            String title,
            String description,
            @Nullable String statusMessage,
            @Nullable String initialName,
            TeamColor initialColor,
            Key submitKey,
            String submitLabel,
            Key backKey
    ) {
        List<DialogBody> body = bodyWithStatus(description, statusMessage);
        List<SingleOptionDialogInput.OptionEntry> colors = new ArrayList<>();
        for (TeamColor color : TeamColor.values()) {
            colors.add(SingleOptionDialogInput.OptionEntry.create(
                    color.key(),
                    Component.text(color.displayName(), TextColor.color(color.rgb())),
                    color == initialColor
            ));
        }
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(title, ACCENT))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text(TeamAdminDialogKeys.INPUT_NAME, Component.text("Team name"))
                                        .width(FIELD_WIDTH)
                                        .maxLength(TeamService.MAX_DISPLAY_NAME_LENGTH)
                                        .initial(initialName == null ? "" : initialName)
                                        .build(),
                                DialogInput.singleOption(
                                                TeamAdminDialogKeys.INPUT_COLOR,
                                                Component.text("Color"),
                                                colors)
                                        .width(FIELD_WIDTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text(submitLabel, CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(submitKey, null))
                                        .build()
                        ))
                        .columns(1)
                        .exitAction(ActionButton.builder(Component.text("Cancel", SECONDARY))
                                .width(BUTTON_WIDTH)
                                .action(DialogAction.customClick(backKey, null))
                                .build())
                        .build()));
    }

    private Dialog emptyPickerDialog(String title, List<DialogBody> body, Key backKey) {
        return Dialog.create(factory -> factory.empty()
                .base(base(title, ACCENT, body))
                .type(DialogType.notice(
                        ActionButton.builder(Component.text("Back", SECONDARY))
                                .width(BUTTON_WIDTH)
                                .action(DialogAction.customClick(backKey, null))
                                .build()
                )));
    }

    private List<SingleOptionDialogInput.OptionEntry> memberOptions(Team team, List<TeamMember> members) {
        List<SingleOptionDialogInput.OptionEntry> options = new ArrayList<>();
        for (int i = 0; i < members.size(); i++) {
            TeamMember member = members.get(i);
            boolean isCaptain = team.captainAccountId() != null
                    && team.captainAccountId().equals(member.accountId());
            options.add(SingleOptionDialogInput.OptionEntry.create(
                    member.username(),
                    Component.text(
                            isCaptain ? member.username() + " (captain)" : member.username(),
                            isCaptain ? NamedTextColor.GOLD : NamedTextColor.WHITE),
                    i == 0
            ));
        }
        return options;
    }

    private AssignLabel labelForAssignOption(Team targetTeam, String username) {
        return teams.findAccountByUsername(username)
                .flatMap(account -> teams.teamOf(account.id()))
                .map(current -> {
                    if (current.id().equals(targetTeam.id())) {
                        return new AssignLabel(username + " (here)", NamedTextColor.GOLD);
                    }
                    return new AssignLabel(
                            username + " (" + current.displayName() + ")",
                            NamedTextColor.YELLOW);
                })
                .orElse(new AssignLabel(username, NamedTextColor.WHITE));
    }

    private static DialogBase base(String title, TextColor color, List<DialogBody> body) {
        return DialogBase.builder(Component.text(title, color))
                .canCloseWithEscape(true)
                // pause+NONE is illegal; pause(false)+NONE keeps navigation instant with no wait overlay.
                .pause(false)
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .build();
    }

    private static ActionButton closeButton() {
        return ActionButton.builder(Component.text("Close", SECONDARY))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(TeamAdminDialogKeys.CLOSE, null))
                .build();
    }

    private static ActionButton backToHubButton() {
        return ActionButton.builder(Component.text("Back", SECONDARY))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_HUB, null))
                .build();
    }

    private static ActionButton backToTeamButton(java.util.UUID teamId) {
        return ActionButton.builder(Component.text("Cancel", SECONDARY))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(TeamAdminDialogKeys.openTeam(teamId), null))
                .build();
    }

    private static List<DialogBody> bodyWithStatus(String description, @Nullable String statusMessage) {
        List<DialogBody> body = new ArrayList<>();
        body.add(plain(description, MUTED));
        appendStatus(body, statusMessage);
        return body;
    }

    private static void appendStatus(List<DialogBody> body, @Nullable String statusMessage) {
        if (statusMessage != null && !statusMessage.isBlank()) {
            body.add(plain(statusMessage, NamedTextColor.YELLOW));
        }
    }

    private static DialogBody plain(String text, TextColor color) {
        return DialogBody.plainMessage(Component.text(text, color), BODY_WIDTH);
    }

    private record AssignLabel(String display, NamedTextColor color) {
    }
}
