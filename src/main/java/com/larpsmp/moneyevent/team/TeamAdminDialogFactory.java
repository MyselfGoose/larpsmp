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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

/**
 * Builds native Paper dialogs for the Teams Admin console.
 */
public final class TeamAdminDialogFactory {

    private static final TextColor ACCENT = TextColor.color(0xD4A017);
    private static final TextColor CONFIRM = TextColor.color(0x5FAF5F);
    private static final TextColor SECONDARY = TextColor.color(0xA0A0A0);
    private static final TextColor DANGER = TextColor.color(0xC07070);
    private static final TextColor MUTED = TextColor.color(0x888888);
    private static final int FIELD_WIDTH = 300;
    private static final int BUTTON_WIDTH = 150;
    private static final int WIDE_BUTTON = 200;

    private final TeamService teams;

    public TeamAdminDialogFactory(TeamService teams) {
        this.teams = teams;
    }

    public Dialog hubDialog(@Nullable String statusMessage) {
        List<Team> teamList = teams.listTeams();
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(
                "Create, monitor, and manage competing teams. Soft roster rules warn during setup.",
                MUTED)));
        if (statusMessage != null && !statusMessage.isBlank()) {
            body.add(DialogBody.plainMessage(Component.text(statusMessage, NamedTextColor.YELLOW)));
        }
        if (teamList.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("No teams exist yet.", DANGER)));
        } else {
            for (Team team : teamList) {
                int members = teams.membersOf(team.id()).size();
                String captain = teams.captainUsername(team).orElse("No captain");
                body.add(DialogBody.plainMessage(Component.text()
                        .append(Component.text(team.displayName(), TextColor.color(team.color().rgb()))
                                .decorate(TextDecoration.BOLD))
                        .append(Component.text("  ·  " + members + "/" + TeamService.EXPECTED_ROSTER_SIZE
                                + "  ·  Captain: " + captain, MUTED))
                        .build()));
            }
        }

        List<ActionButton> actions = new ArrayList<>();
        for (Team team : teamList) {
            actions.add(ActionButton.builder(Component.text(team.displayName(), TextColor.color(team.color().rgb())))
                    .width(BUTTON_WIDTH)
                    .tooltip(Component.text("Open " + team.displayName()))
                    .action(DialogAction.customClick(TeamAdminDialogKeys.openTeam(team.id()), null))
                    .build());
        }
        actions.add(ActionButton.builder(Component.text("Create Team", CONFIRM))
                .width(BUTTON_WIDTH)
                .tooltip(Component.text("Create a new team"))
                .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_CREATE, null))
                .build());
        actions.add(ActionButton.builder(Component.text("Warnings", NamedTextColor.GOLD))
                .width(BUTTON_WIDTH)
                .tooltip(Component.text("Show roster setup warnings"))
                .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_WARNINGS, null))
                .build());

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Teams Admin", ACCENT))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(actions)
                        .columns(2)
                        .exitAction(closeButton())
                        .build()));
    }

    public Dialog createTeamDialog(@Nullable String statusMessage) {
        return formDialog(
                "Create Team",
                "Choose a display name and color. Competition expects three teams.",
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
                "Update the team name or color.",
                statusMessage,
                team.displayName(),
                team.color(),
                TeamAdminDialogKeys.EDIT_SUBMIT,
                "Save",
                TeamAdminDialogKeys.openTeam(team.id())
        );
    }

    public Dialog teamDetailDialog(Team team, @Nullable String statusMessage) {
        List<TeamMember> members = teams.membersOf(team.id());
        String captain = teams.captainUsername(team).orElse("No captain");
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text()
                .append(Component.text(team.displayName(), TextColor.color(team.color().rgb()))
                        .decorate(TextDecoration.BOLD))
                .append(Component.text("  ·  " + team.color().displayName(), MUTED))
                .build()));
        body.add(DialogBody.plainMessage(Component.text(
                "Members: " + members.size() + "/" + TeamService.EXPECTED_ROSTER_SIZE
                        + "  ·  Captain: " + captain,
                MUTED)));
        if (statusMessage != null && !statusMessage.isBlank()) {
            body.add(DialogBody.plainMessage(Component.text(statusMessage, NamedTextColor.YELLOW)));
        }
        if (members.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("Roster is empty.", DANGER)));
        } else {
            for (TeamMember member : members) {
                boolean isCaptain = team.captainAccountId() != null
                        && team.captainAccountId().equals(member.accountId());
                body.add(DialogBody.plainMessage(Component.text()
                        .append(Component.text("• " + member.username(), NamedTextColor.WHITE))
                        .append(isCaptain
                                ? Component.text("  (captain)", NamedTextColor.GOLD)
                                : Component.empty())
                        .build()));
            }
        }

        List<ActionButton> actions = List.of(
                ActionButton.builder(Component.text("Members", CONFIRM))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Assign or remove members"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_MEMBERS, null))
                        .build(),
                ActionButton.builder(Component.text("Set Captain", NamedTextColor.GOLD))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Choose a captain from the roster"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_CAPTAIN, null))
                        .build(),
                ActionButton.builder(Component.text("Edit", SECONDARY))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Rename or recolor"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_EDIT, null))
                        .build(),
                ActionButton.builder(Component.text("Delete", DANGER))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Delete this team"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_DELETE, null))
                        .build(),
                ActionButton.builder(Component.text("Back", SECONDARY))
                        .width(BUTTON_WIDTH)
                        .tooltip(Component.text("Return to teams hub"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_HUB, null))
                        .build()
        );

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(team.displayName(), TextColor.color(team.color().rgb())))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(actions)
                        .columns(2)
                        .exitAction(closeButton())
                        .build()));
    }

    public Dialog membersDialog(Team team, @Nullable String statusMessage) {
        List<TeamMember> members = teams.membersOf(team.id());
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(
                "Assign players by LarpSMP username. Moving a player from another team is allowed.",
                MUTED)));
        if (statusMessage != null && !statusMessage.isBlank()) {
            body.add(DialogBody.plainMessage(Component.text(statusMessage, NamedTextColor.YELLOW)));
        }
        if (members.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("No members yet.", MUTED)));
        }

        List<ActionButton> actions = new ArrayList<>();
        actions.add(ActionButton.builder(Component.text("Assign Player", CONFIRM))
                .width(WIDE_BUTTON)
                .tooltip(Component.text("Add or move a player onto this team"))
                .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_ASSIGN, null))
                .build());
        for (TeamMember member : members) {
            actions.add(ActionButton.builder(Component.text("Remove " + member.username(), DANGER))
                    .width(WIDE_BUTTON)
                    .tooltip(Component.text("Remove " + member.username() + " from " + team.displayName()))
                    .action(DialogAction.customClick(TeamAdminDialogKeys.removeMember(member.accountId()), null))
                    .build());
        }
        actions.add(ActionButton.builder(Component.text("Back", SECONDARY))
                .width(BUTTON_WIDTH)
                .tooltip(Component.text("Return to team"))
                .action(DialogAction.customClick(TeamAdminDialogKeys.openTeam(team.id()), null))
                .build());

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(
                                team.displayName() + " Members", TextColor.color(team.color().rgb())))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(actions)
                        .columns(1)
                        .exitAction(closeButton())
                        .build()));
    }

    public Dialog assignDialog(Team team, @Nullable String statusMessage) {
        List<DialogBody> body = bodyWithStatus(
                "Enter the LarpSMP username of the player to assign to " + team.displayName() + ".",
                statusMessage);
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Assign to " + team.displayName(), ACCENT))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text(TeamAdminDialogKeys.INPUT_USERNAME, Component.text("Username"))
                                        .width(FIELD_WIDTH)
                                        .maxLength(64)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text("Assign", CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(TeamAdminDialogKeys.ASSIGN_SUBMIT, null))
                                        .build(),
                                ActionButton.builder(Component.text("Back", SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_MEMBERS, null))
                                        .build()
                        ))
                        .columns(2)
                        .exitAction(closeButton())
                        .build()));
    }

    public Dialog captainDialog(Team team, @Nullable String statusMessage) {
        List<TeamMember> members = teams.membersOf(team.id());
        List<DialogBody> body = bodyWithStatus(
                "Captain must already be on " + team.displayName() + ". Captain abilities come later.",
                statusMessage);
        List<ActionButton> actions = new ArrayList<>();
        if (members.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text(
                    "Assign members before choosing a captain.", DANGER)));
        } else {
            for (TeamMember member : members) {
                boolean selected = team.captainAccountId() != null
                        && team.captainAccountId().equals(member.accountId());
                String label = selected ? "★ " + member.username() : member.username();
                actions.add(ActionButton.builder(Component.text(label, selected ? NamedTextColor.GOLD : NamedTextColor.WHITE))
                        .width(WIDE_BUTTON)
                        .tooltip(Component.text("Set " + member.username() + " as captain"))
                        .action(DialogAction.customClick(TeamAdminDialogKeys.setCaptain(member.accountId()), null))
                        .build());
            }
        }
        if (team.captainAccountId() != null) {
            actions.add(ActionButton.builder(Component.text("Clear Captain", DANGER))
                    .width(BUTTON_WIDTH)
                    .action(DialogAction.customClick(TeamAdminDialogKeys.CLEAR_CAPTAIN, null))
                    .build());
        }
        actions.add(ActionButton.builder(Component.text("Back", SECONDARY))
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(TeamAdminDialogKeys.openTeam(team.id()), null))
                .build());

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Captain — " + team.displayName(), ACCENT))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(actions)
                        .columns(1)
                        .exitAction(closeButton())
                        .build()));
    }

    public Dialog deleteConfirmDialog(Team team, @Nullable String statusMessage) {
        int members = teams.membersOf(team.id()).size();
        List<DialogBody> body = bodyWithStatus(
                "Delete " + team.displayName() + "? This removes " + members
                        + " membership(s) and clears the captain. This cannot be undone.",
                statusMessage);
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Delete " + team.displayName() + "?", DANGER))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text("Delete", DANGER))
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
            body.add(DialogBody.plainMessage(Component.text(
                    "No roster warnings. Teams look competition-ready.", CONFIRM)));
        } else {
            body.add(DialogBody.plainMessage(Component.text(
                    "Soft setup warnings (edits are still allowed):", MUTED)));
            for (String warning : warnings) {
                body.add(DialogBody.plainMessage(Component.text("• " + warning, NamedTextColor.GOLD)));
            }
        }
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Roster Warnings", ACCENT))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text("Back", SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(TeamAdminDialogKeys.OPEN_HUB, null))
                                        .build()
                        ))
                        .columns(1)
                        .exitAction(closeButton())
                        .build()));
    }

    private Dialog formDialog(
            String title,
            String description,
            @Nullable String statusMessage,
            @Nullable String initialName,
            TeamColor initialColor,
            net.kyori.adventure.key.Key submitKey,
            String submitLabel,
            net.kyori.adventure.key.Key backKey
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
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
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
                                        .build(),
                                ActionButton.builder(Component.text("Back", SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(backKey, null))
                                        .build()
                        ))
                        .columns(2)
                        .exitAction(closeButton())
                        .build()));
    }

    private static List<DialogBody> bodyWithStatus(String description, @Nullable String statusMessage) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(description, MUTED)));
        if (statusMessage != null && !statusMessage.isBlank()) {
            body.add(DialogBody.plainMessage(Component.text(statusMessage, NamedTextColor.YELLOW)));
        }
        return body;
    }

    private static ActionButton closeButton() {
        return ActionButton.builder(Component.text("Close", SECONDARY))
                .width(BUTTON_WIDTH)
                .tooltip(Component.text("Close Teams Admin"))
                .action(DialogAction.customClick(TeamAdminDialogKeys.CLOSE, null))
                .build();
    }
}
