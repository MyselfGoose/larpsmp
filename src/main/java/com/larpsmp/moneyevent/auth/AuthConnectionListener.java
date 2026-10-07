package com.larpsmp.moneyevent.auth;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
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
    private final @Nullable AuthService authService;
    private final AuthSessionManager sessionManager;
    private final AuthDialogFactory dialogFactory;
    private final ExecutorService authExecutor;
    private final Logger logger;
    private final boolean databaseReady;

    public AuthConnectionListener(
            AuthConfig config,
            @Nullable AuthService authService,
            AuthSessionManager sessionManager,
            AuthDialogFactory dialogFactory,
            ExecutorService authExecutor,
            boolean databaseReady,
            Logger logger
    ) {
        this.config = config;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.dialogFactory = dialogFactory;
        this.authExecutor = authExecutor;
        this.databaseReady = databaseReady;
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

        if (!databaseReady || authService == null) {
            connection.disconnect(Component.text(
                    config.messages().disconnectDatabaseUnavailable(),
                    NamedTextColor.RED
            ));
            return;
        }

        AuthSession session = sessionManager.begin(connection, profileId, config.timeoutSeconds());
        Audience audience = connection.getAudience();
        audience.showDialog(dialogFactory.loginDialog(null));

        AuthResult outcome;
        try {
            outcome = session.result().join();
        } catch (Exception exception) {
            logger.warning("Authentication wait failed for profile " + profileId + ": " + exception.getMessage());
            outcome = AuthResult.REJECTED;
        } finally {
            sessionManager.remove(profileId);
        }

        if (outcome == null) {
            outcome = AuthResult.REJECTED;
        }

        switch (outcome) {
            case ALLOWED -> audience.closeDialog();
            case CANCELLED -> {
                // Player already disconnected via Back to main menu.
            }
            case REJECTED -> {
                audience.closeDialog();
                if (connection.isConnected()) {
                    connection.disconnect(Component.text(config.messages().disconnectTimeout(), NamedTextColor.RED));
                }
            }
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
        String minecraftName = connection.getProfile().getName();

        if (identifier.equals(AuthDialogKeys.LOGIN)) {
            handleLogin(session, profileId, minecraftName, audience, view);
            return;
        }
        if (identifier.equals(AuthDialogKeys.OPEN_SIGNUP)) {
            if (!config.signup().enabled()) {
                audience.showDialog(dialogFactory.loginDialog(config.messages().signupDisabled()));
                return;
            }
            audience.showDialog(dialogFactory.signupDialog(null));
            return;
        }
        if (identifier.equals(AuthDialogKeys.SIGNUP)) {
            handleSignup(session, profileId, minecraftName, audience, view);
            return;
        }
        if (identifier.equals(AuthDialogKeys.OPEN_LOGIN)) {
            audience.showDialog(dialogFactory.loginDialog(null));
            return;
        }
        if (identifier.equals(AuthDialogKeys.BACK_TO_MENU)) {
            handleBackToMenu(profileId, connection, audience);
        }
    }

    private void handleBackToMenu(UUID profileId, PlayerConfigurationConnection connection, Audience audience) {
        if (!sessionManager.complete(profileId, AuthResult.CANCELLED)) {
            return;
        }
        audience.closeDialog();
        if (connection.isConnected()) {
            connection.disconnect(Component.text(config.messages().disconnectCancelled(), NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onConnectionClose(PlayerConnectionCloseEvent event) {
        sessionManager.cancel(event.getPlayerUniqueId());
    }

    private void handleLogin(
            AuthSession session,
            UUID profileId,
            @Nullable String minecraftName,
            Audience audience,
            @Nullable DialogResponseView view
    ) {
        AuthService service = authService;
        if (service == null || !databaseReady) {
            audience.showDialog(dialogFactory.loginDialog(config.messages().loginInternalError()));
            return;
        }

        String identifier = textOrEmpty(view, AuthDialogKeys.INPUT_IDENTIFIER);
        String password = textOrEmpty(view, AuthDialogKeys.INPUT_PASSWORD);

        if (service.hasEmptyLoginFields(identifier, password)) {
            audience.showDialog(dialogFactory.loginDialog(config.messages().loginEmptyFields()));
            return;
        }

        if (!session.tryStartProcessing()) {
            return;
        }

        authExecutor.execute(() -> {
            try {
                LoginResult result = service.login(identifier, password, profileId, minecraftName);
                applyLoginResult(session, profileId, audience, result);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unexpected login error for profile " + profileId, exception);
                if (session.isPending()) {
                    audience.showDialog(dialogFactory.loginDialog(config.messages().loginInternalError()));
                }
            } finally {
                session.finishProcessing();
            }
        });
    }

    private void handleSignup(
            AuthSession session,
            UUID profileId,
            @Nullable String minecraftName,
            Audience audience,
            @Nullable DialogResponseView view
    ) {
        if (!config.signup().enabled()) {
            audience.showDialog(dialogFactory.loginDialog(config.messages().signupDisabled()));
            return;
        }

        AuthService service = authService;
        if (service == null || !databaseReady) {
            audience.showDialog(dialogFactory.signupDialog(config.messages().signupInternalError()));
            return;
        }

        String username = textOrEmpty(view, AuthDialogKeys.INPUT_USERNAME);
        String email = textOrEmpty(view, AuthDialogKeys.INPUT_EMAIL);
        String password = textOrEmpty(view, AuthDialogKeys.INPUT_PASSWORD);

        var validationError = service.validateSignupFields(username, email, password);
        if (validationError.isPresent()) {
            audience.showDialog(dialogFactory.signupDialog(validationError.get()));
            return;
        }

        if (!session.tryStartProcessing()) {
            return;
        }

        authExecutor.execute(() -> {
            try {
                SignupResult result = service.signup(username, email, password, profileId, minecraftName);
                applySignupResult(session, profileId, audience, result);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unexpected signup error for profile " + profileId, exception);
                if (session.isPending()) {
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupInternalError()));
                }
            } finally {
                session.finishProcessing();
            }
        });
    }

    private void applyLoginResult(AuthSession session, UUID profileId, Audience audience, LoginResult result) {
        if (!session.isPending()) {
            return;
        }

        switch (result) {
            case LoginResult.Success ignored -> {
                if (sessionManager.complete(profileId, AuthResult.ALLOWED)) {
                    audience.closeDialog();
                }
            }
            case LoginResult.InvalidCredentials ignored ->
                    audience.showDialog(dialogFactory.loginDialog(config.messages().loginInvalidCredentials()));
            case LoginResult.UuidBoundToOtherAccount ignored ->
                    audience.showDialog(dialogFactory.loginDialog(config.messages().loginUuidBoundOther()));
            case LoginResult.AccountBoundToOtherUuid ignored ->
                    audience.showDialog(dialogFactory.loginDialog(config.messages().loginAccountBoundOther()));
            case LoginResult.RateLimited ignored ->
                    audience.showDialog(dialogFactory.loginDialog(config.messages().loginRateLimited()));
            case LoginResult.InternalError ignored ->
                    audience.showDialog(dialogFactory.loginDialog(config.messages().loginInternalError()));
        }
    }

    private void applySignupResult(AuthSession session, UUID profileId, Audience audience, SignupResult result) {
        if (!session.isPending()) {
            return;
        }

        switch (result) {
            case SignupResult.Success ignored -> {
                // Auto-login after successful signup for better UX.
                if (sessionManager.complete(profileId, AuthResult.ALLOWED)) {
                    audience.closeDialog();
                }
            }
            case SignupResult.ValidationError validationError ->
                    audience.showDialog(dialogFactory.signupDialog(validationError.message()));
            case SignupResult.UsernameTaken ignored ->
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupUsernameTaken()));
            case SignupResult.EmailTaken ignored ->
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupEmailTaken()));
            case SignupResult.RateLimited ignored ->
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupRateLimited()));
            case SignupResult.InternalError ignored ->
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupInternalError()));
        }
    }

    private static String textOrEmpty(@Nullable DialogResponseView view, String key) {
        if (view == null) {
            return "";
        }
        String value = view.getText(key);
        return value == null ? "" : value;
    }
}
