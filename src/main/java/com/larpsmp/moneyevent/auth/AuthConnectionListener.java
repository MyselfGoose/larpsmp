package com.larpsmp.moneyevent.auth;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.UUID;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

/**
 * Blocks world join until the connecting player authenticates via dialog UI.
 */
public final class AuthConnectionListener implements Listener {

    private final AuthConfig config;
    private final AuthCredentialsValidator validator;
    private final AuthSessionManager sessionManager;
    private final AuthDialogFactory dialogFactory;
    private final Logger logger;

    public AuthConnectionListener(
            AuthConfig config,
            AuthCredentialsValidator validator,
            AuthSessionManager sessionManager,
            AuthDialogFactory dialogFactory,
            Logger logger
    ) {
        this.config = config;
        this.validator = validator;
        this.sessionManager = sessionManager;
        this.dialogFactory = dialogFactory;
        this.logger = logger;
    }

    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        if (!config.enabled()) {
            return;
        }

        PlayerConfigurationConnection connection = event.getConnection();
        UUID profileId = connection.getProfile().getId();
        if (profileId == null) {
            connection.disconnect(Component.text(config.messages().disconnectMissingProfile(), NamedTextColor.RED));
            return;
        }

        AuthSession session = sessionManager.begin(connection, profileId, config.timeoutSeconds());
        Audience audience = connection.getAudience();
        audience.showDialog(dialogFactory.loginDialog(null));

        boolean allowed;
        try {
            allowed = Boolean.TRUE.equals(session.result().join());
        } catch (Exception exception) {
            logger.warning("Authentication wait failed for profile " + profileId + ": " + exception.getMessage());
            allowed = false;
        } finally {
            sessionManager.remove(profileId);
        }

        if (allowed) {
            audience.closeDialog();
            return;
        }

        audience.closeDialog();
        if (connection.isConnected()) {
            connection.disconnect(Component.text(config.messages().disconnectTimeout(), NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerConfigurationConnection connection)) {
            return;
        }

        UUID profileId = connection.getProfile().getId();
        if (profileId == null) {
            return;
        }

        AuthSession session = sessionManager.get(profileId).orElse(null);
        if (session == null || !session.isPending()) {
            return;
        }

        Key identifier = event.getIdentifier();
        Audience audience = connection.getAudience();
        DialogResponseView view = event.getDialogResponseView();

        if (identifier.equals(AuthDialogKeys.LOGIN)) {
            handleLogin(profileId, audience, view);
            return;
        }
        if (identifier.equals(AuthDialogKeys.OPEN_SIGNUP)) {
            audience.showDialog(dialogFactory.signupDialog(null));
            return;
        }
        if (identifier.equals(AuthDialogKeys.SIGNUP)) {
            handleSignup(audience, view);
            return;
        }
        if (identifier.equals(AuthDialogKeys.OPEN_LOGIN)) {
            audience.showDialog(dialogFactory.loginDialog(null));
        }
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        sessionManager.cancel(event.getPlayerUniqueId());
    }

    private void handleLogin(UUID profileId, Audience audience, @Nullable DialogResponseView view) {
        String identifier = textOrEmpty(view, AuthDialogKeys.INPUT_IDENTIFIER);
        String password = textOrEmpty(view, AuthDialogKeys.INPUT_PASSWORD);

        if (validator.hasEmptyLoginFields(identifier, password)) {
            audience.showDialog(dialogFactory.loginDialog(config.messages().loginEmptyFields()));
            return;
        }

        if (!validator.authenticate(identifier, password)) {
            audience.showDialog(dialogFactory.loginDialog(config.messages().loginInvalidCredentials()));
            return;
        }

        sessionManager.complete(profileId, true);
        audience.closeDialog();
    }

    private void handleSignup(Audience audience, @Nullable DialogResponseView view) {
        String username = textOrEmpty(view, AuthDialogKeys.INPUT_USERNAME);
        String email = textOrEmpty(view, AuthDialogKeys.INPUT_EMAIL);
        String password = textOrEmpty(view, AuthDialogKeys.INPUT_PASSWORD);

        var validationError = validator.validateSignupFields(
                username,
                email,
                password,
                config.messages().signupEmptyFields()
        );
        if (validationError.isPresent()) {
            audience.showDialog(dialogFactory.signupDialog(validationError.get()));
            return;
        }

        // UI-only phase: do not create accounts or grant world access from signup.
        audience.showDialog(dialogFactory.signupUnavailableDialog());
    }

    private static String textOrEmpty(@Nullable DialogResponseView view, String key) {
        if (view == null) {
            return "";
        }
        String value = view.getText(key);
        return value == null ? "" : value;
    }
}
