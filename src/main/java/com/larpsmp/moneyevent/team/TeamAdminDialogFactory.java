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
import org.jetbrains.annotations.Nullable;

/**
 * Paper Dialogs used only for typed admin forms (create / edit / assign).
 * Browsing and management use {@link TeamAdminMenus}.
 */
public final class TeamAdminDialogFactory {

    private static final TextColor ACCENT = TextColor.color(0xD4A017);
    private static final TextColor CONFIRM = TextColor.color(0x5FAF5F);
    private static final TextColor SECONDARY = TextColor.color(0xA0A0A0);
    private static final TextColor MUTED = TextColor.color(0x888888);
    private static final int FIELD_WIDTH = 300;
    private static final int BUTTON_WIDTH = 150;

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
                "Update the team name or color.",
                statusMessage,
                team.displayName(),
                team.color(),
                TeamAdminDialogKeys.EDIT_SUBMIT,
                "Save",
                TeamAdminDialogKeys.openTeam(team.id())
        );
    }

    public Dialog assignDialog(Team team, @Nullable String statusMessage) {
        List<DialogBody> body = bodyWithStatus(
                "Enter the LarpSMP username to assign to " + team.displayName() + ".",
                statusMessage);
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Assign Player", ACCENT))
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
                                ActionButton.builder(Component.text("Cancel", SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(TeamAdminDialogKeys.openTeam(team.id()), null))
                                        .build()
                        ))
                        .columns(2)
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
                                ActionButton.builder(Component.text("Cancel", SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(backKey, null))
                                        .build()
                        ))
                        .columns(2)
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
}
