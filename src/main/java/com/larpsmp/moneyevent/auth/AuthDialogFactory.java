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
    private static final TextColor EXIT = TextColor.color(0xC07070);
    private static final int FIELD_WIDTH = 300;
    private static final int BUTTON_WIDTH = 150;
    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final int MAX_PASSWORD_LENGTH = 64;
    private static final int MAX_CODE_LENGTH = 10;

    private final AuthConfig.Messages messages;

    public AuthDialogFactory(AuthConfig.Messages messages) {
        this.messages = messages;
    }

    public Dialog loginDialog(@Nullable String errorMessage) {
        List<DialogBody> body = bodyWithOptionalError(messages.loginBody(), errorMessage);
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
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text(messages.loginSubmit(), CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .tooltip(Component.text("Authenticate with your account"))
                                        .action(DialogAction.customClick(AuthDialogKeys.LOGIN, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.loginOpenSignup(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .tooltip(Component.text("Open the sign up form"))
                                        .action(DialogAction.customClick(AuthDialogKeys.OPEN_SIGNUP, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.loginForgotPassword(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .tooltip(Component.text("Recover password or username"))
                                        .action(DialogAction.customClick(AuthDialogKeys.OPEN_FORGOT, null))
                                        .build()
                        ))
                        .columns(2)
                        .exitAction(backToMenuButton())
                        .build()));
    }

    public Dialog signupDialog(@Nullable String errorMessage) {
        List<DialogBody> body = bodyWithOptionalError(messages.signupBody(), errorMessage);
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
                .type(DialogType.multiAction(List.of(
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
                        ))
                        .columns(2)
                        .exitAction(backToMenuButton())
                        .build()));
    }

    public Dialog verifyEmailDialog(String maskedEmail, @Nullable String errorMessage) {
        String bodyText = messages.verifyBody().replace("{email}", maskedEmail == null ? "***" : maskedEmail);
        List<DialogBody> body = bodyWithOptionalError(bodyText, errorMessage);
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(messages.verifyTitle(), ACCENT))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text(AuthDialogKeys.INPUT_CODE, Component.text(messages.verifyCodeLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_CODE_LENGTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text(messages.verifySubmit(), CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.VERIFY_EMAIL, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.verifyResend(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.VERIFY_RESEND, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.verifyBack(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.OPEN_LOGIN, null))
                                        .build()
                        ))
                        .columns(2)
                        .exitAction(backToMenuButton())
                        .build()));
    }

    public Dialog forgotHubDialog(@Nullable String errorMessage) {
        List<DialogBody> body = bodyWithOptionalError(messages.forgotHubBody(), errorMessage);
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(messages.forgotHubTitle(), ACCENT))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text(messages.forgotChangePassword(), CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.FORGOT_CHANGE_PASSWORD, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.forgotRecoverUsername(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.FORGOT_RECOVER_USERNAME, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.forgotBack(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.OPEN_LOGIN, null))
                                        .build()
                        ))
                        .columns(2)
                        .exitAction(backToMenuButton())
                        .build()));
    }

    public Dialog forgotEmailDialog(@Nullable String errorMessage) {
        List<DialogBody> body = bodyWithOptionalError(messages.forgotEmailBody(), errorMessage);
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(messages.forgotEmailTitle(), ACCENT))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text(AuthDialogKeys.INPUT_EMAIL, Component.text(messages.forgotEmailLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_IDENTIFIER_LENGTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text(messages.forgotEmailSubmit(), CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.FORGOT_SEND_CODE, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.forgotBack(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.OPEN_FORGOT, null))
                                        .build()
                        ))
                        .columns(2)
                        .exitAction(backToMenuButton())
                        .build()));
    }

    public Dialog forgotVerifyDialog(String maskedEmail, @Nullable String errorMessage) {
        String bodyText = messages.verifyBody().replace("{email}", maskedEmail == null ? "***" : maskedEmail);
        List<DialogBody> body = bodyWithOptionalError(bodyText, errorMessage);
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(messages.verifyTitle(), ACCENT))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text(AuthDialogKeys.INPUT_CODE, Component.text(messages.verifyCodeLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_CODE_LENGTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text(messages.verifySubmit(), CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.FORGOT_VERIFY, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.verifyResend(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.FORGOT_RESEND, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.forgotBack(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.OPEN_FORGOT, null))
                                        .build()
                        ))
                        .columns(2)
                        .exitAction(backToMenuButton())
                        .build()));
    }

    public Dialog resetPasswordDialog(@Nullable String errorMessage) {
        List<DialogBody> body = bodyWithOptionalError(messages.resetPasswordBody(), errorMessage);
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(messages.resetPasswordTitle(), ACCENT))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .inputs(List.of(
                                DialogInput.text(AuthDialogKeys.INPUT_PASSWORD, Component.text(messages.resetPasswordLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_PASSWORD_LENGTH)
                                        .build(),
                                DialogInput.text(AuthDialogKeys.INPUT_PASSWORD_CONFIRM, Component.text(messages.resetPasswordConfirmLabel()))
                                        .width(FIELD_WIDTH)
                                        .maxLength(MAX_PASSWORD_LENGTH)
                                        .build()
                        ))
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text(messages.resetPasswordSubmit(), CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.RESET_PASSWORD, null))
                                        .build(),
                                ActionButton.builder(Component.text(messages.forgotBack(), SECONDARY))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.OPEN_LOGIN, null))
                                        .build()
                        ))
                        .columns(2)
                        .exitAction(backToMenuButton())
                        .build()));
    }

    public Dialog usernameRevealDialog(String username) {
        String bodyText = messages.usernameRevealBody().replace("{username}", username == null ? "?" : username);
        List<DialogBody> body = List.of(
                DialogBody.plainMessage(Component.text(bodyText, NamedTextColor.GRAY))
        );
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text(messages.usernameRevealTitle(), ACCENT))
                        .canCloseWithEscape(false)
                        .afterAction(DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(List.of(
                                ActionButton.builder(Component.text(messages.usernameRevealBack(), CONFIRM))
                                        .width(BUTTON_WIDTH)
                                        .action(DialogAction.customClick(AuthDialogKeys.OPEN_LOGIN, null))
                                        .build()
                        ))
                        .columns(1)
                        .exitAction(backToMenuButton())
                        .build()));
    }

    private List<DialogBody> bodyWithOptionalError(String bodyText, @Nullable String errorMessage) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(bodyText, NamedTextColor.GRAY)));
        if (errorMessage != null && !errorMessage.isBlank()) {
            body.add(DialogBody.plainMessage(Component.text(errorMessage, NamedTextColor.RED)));
        }
        return body;
    }

    private ActionButton backToMenuButton() {
        return ActionButton.builder(Component.text(messages.backToMenu(), EXIT))
                .width(BUTTON_WIDTH)
                .tooltip(Component.text("Disconnect and return to the Minecraft main menu"))
                .action(DialogAction.customClick(AuthDialogKeys.BACK_TO_MENU, null))
                .build();
    }
}
