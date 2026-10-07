package com.larpsmp.moneyevent.auth;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

/**
 * Builds native Paper dialogs for the pre-join authentication gate.
 */
public final class AuthDialogFactory {

    private static final TextColor ACCENT = TextColor.color(0xD4A017);
    private static final TextColor CONFIRM = TextColor.color(0x5FAF5F);
    private static final TextColor SECONDARY = TextColor.color(0xA0A0A0);
    private static final int FIELD_WIDTH = 300;
    private static final int BUTTON_WIDTH = 150;
    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final int MAX_PASSWORD_LENGTH = 64;

    private final AuthConfig.Messages messages;

    public AuthDialogFactory(AuthConfig.Messages messages) {
        this.messages = messages;
    }

    public Dialog loginDialog(@Nullable String errorMessage) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(messages.loginBody(), NamedTextColor.GRAY)));
        if (errorMessage != null && !errorMessage.isBlank()) {
            body.add(DialogBody.plainMessage(Component.text(errorMessage, NamedTextColor.RED)));
        }

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(messages.loginTitle(), ACCENT))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text(AuthDialogKeys.INPUT_IDENTIFIER, Component.text(messages.loginIdentifierLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_IDENTIFIER_LENGTH)
                                        .build(),
                                DialogInput.text(AuthDialogKeys.INPUT_PASSWORD, Component.text(messages.loginPasswordLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_PASSWORD_LENGTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text(messages.loginSubmit(), CONFIRM))
                                .width(BUTTON_WIDTH)
                                .tooltip(Component.text("Authenticate with your account"))
                                .action(DialogAction.customClick(AuthDialogKeys.LOGIN, null))
                                .build(),
                        ActionButton.builder(Component.text(messages.loginOpenSignup(), SECONDARY))
                                .width(BUTTON_WIDTH)
                                .tooltip(Component.text("Open the sign up form"))
                                .action(DialogAction.customClick(AuthDialogKeys.OPEN_SIGNUP, null))
                                .build()
                )));
    }

    public Dialog signupDialog(@Nullable String errorMessage) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(messages.signupBody(), NamedTextColor.GRAY)));
        if (errorMessage != null && !errorMessage.isBlank()) {
            body.add(DialogBody.plainMessage(Component.text(errorMessage, NamedTextColor.RED)));
        }

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(messages.signupTitle(), ACCENT))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text(AuthDialogKeys.INPUT_USERNAME, Component.text(messages.signupUsernameLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_IDENTIFIER_LENGTH)
                                        .build(),
                                DialogInput.text(AuthDialogKeys.INPUT_EMAIL, Component.text(messages.signupEmailLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_IDENTIFIER_LENGTH)
                                        .build(),
                                DialogInput.text(AuthDialogKeys.INPUT_PASSWORD, Component.text(messages.signupPasswordLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_PASSWORD_LENGTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.builder(Component.text(messages.signupSubmit(), CONFIRM))
                                .width(BUTTON_WIDTH)
                                .tooltip(Component.text("Submit the sign up form"))
                                .action(DialogAction.customClick(AuthDialogKeys.SIGNUP, null))
                                .build(),
                        ActionButton.builder(Component.text(messages.signupBack(), SECONDARY))
                                .width(BUTTON_WIDTH)
                                .tooltip(Component.text("Return to login"))
                                .action(DialogAction.customClick(AuthDialogKeys.OPEN_LOGIN, null))
                                .build()
                )));
    }

    public Dialog signupUnavailableDialog() {
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(messages.signupUnavailableTitle(), ACCENT))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(List.of(
                                DialogBody.plainMessage(Component.text(messages.signupUnavailableBody(), NamedTextColor.GRAY))
                        ))
                        .build())
                .type(DialogType.notice(
                        ActionButton.builder(Component.text(messages.signupUnavailableAck(), CONFIRM))
                                .width(BUTTON_WIDTH)
                                .action(DialogAction.customClick(AuthDialogKeys.OPEN_LOGIN, null))
                                .build()
                )));
    }
}
