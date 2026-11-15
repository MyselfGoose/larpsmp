package com.larpsmp.moneyevent.auth;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.larpsmp.moneyevent.playerstate.AccountPlayerState;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.playerstate.PlayerStateService;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.Optional;
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
    private final AccountSessionManager accountSessions;
    private final @Nullable PlayerStateService playerStates;
    private final AuthDialogFactory dialogFactory;
    private final ExecutorService authExecutor;
    private final Logger logger;
    private final boolean databaseReady;

    public AuthConnectionListener(
            AuthConfig config,
            @Nullable AuthService authService,
            AuthSessionManager sessionManager,
            AccountSessionManager accountSessions,
            @Nullable PlayerStateService playerStates,
            AuthDialogFactory dialogFactory,
            ExecutorService authExecutor,
            boolean databaseReady,
            Logger logger
    ) {
        this.config = config;
        this.authService = authService;
        this.sessionManager = sessionManager;
        this.accountSessions = accountSessions;
        this.playerStates = playerStates;
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
                accountSessions.cancelPending(profileId);
            }
            case REJECTED -> {
                accountSessions.cancelPending(profileId);
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
            session.flow().showSignup();
            audience.showDialog(dialogFactory.signupDialog(null));
            return;
        }
        if (identifier.equals(AuthDialogKeys.SIGNUP)) {
            handleSignup(session, profileId, minecraftName, audience, view);
            return;
        }
        if (identifier.equals(AuthDialogKeys.OPEN_LOGIN)) {
            session.flow().showLogin();
            audience.showDialog(dialogFactory.loginDialog(null));
            return;
        }
        if (identifier.equals(AuthDialogKeys.OPEN_FORGOT)) {
            session.flow().showForgotHub();
            audience.showDialog(dialogFactory.forgotHubDialog(null));
            return;
        }
        if (identifier.equals(AuthDialogKeys.FORGOT_CHANGE_PASSWORD)) {
            session.flow().beginForgotEmail(EmailChallengePurpose.PASSWORD_RESET);
            audience.showDialog(dialogFactory.forgotEmailDialog(null));
            return;
        }
        if (identifier.equals(AuthDialogKeys.FORGOT_RECOVER_USERNAME)) {
            session.flow().beginForgotEmail(EmailChallengePurpose.USERNAME_RECOVERY);
            audience.showDialog(dialogFactory.forgotEmailDialog(null));
            return;
        }
        if (identifier.equals(AuthDialogKeys.FORGOT_SEND_CODE)) {
            handleForgotSendCode(session, profileId, audience, view);
            return;
        }
        if (identifier.equals(AuthDialogKeys.FORGOT_VERIFY)) {
            handleForgotVerify(session, profileId, audience, view);
            return;
        }
        if (identifier.equals(AuthDialogKeys.FORGOT_RESEND)) {
            handleForgotResend(session, audience);
            return;
        }
        if (identifier.equals(AuthDialogKeys.RESET_PASSWORD)) {
            handleResetPassword(session, audience, view);
            return;
        }
        if (identifier.equals(AuthDialogKeys.VERIFY_EMAIL)) {
            handleVerifyEmail(session, profileId, audience, view);
            return;
        }
        if (identifier.equals(AuthDialogKeys.VERIFY_RESEND)) {
            handleVerifyResend(session, audience);
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
        accountSessions.cancelPending(event.getPlayerUniqueId());
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
                applySignupResult(session, audience, result);
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

    private void handleVerifyEmail(
            AuthSession session,
            UUID profileId,
            Audience audience,
            @Nullable DialogResponseView view
    ) {
        AuthService service = authService;
        UUID accountId = session.flow().accountId();
        if (service == null || accountId == null) {
            audience.showDialog(dialogFactory.loginDialog(config.messages().verifyInternalError()));
            return;
        }

        String code = textOrEmpty(view, AuthDialogKeys.INPUT_CODE);
        if (code.isBlank()) {
            audience.showDialog(dialogFactory.verifyEmailDialog(
                    session.flow().maskedEmail(),
                    config.messages().verifyEmptyCode()
            ));
            return;
        }

        if (!session.tryStartProcessing()) {
            return;
        }

        authExecutor.execute(() -> {
            try {
                VerifyEmailResult result = service.verifySignupEmail(accountId, code, profileId);
                applyVerifyEmailResult(session, profileId, audience, result);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unexpected verify error for profile " + profileId, exception);
                if (session.isPending()) {
                    audience.showDialog(dialogFactory.verifyEmailDialog(
                            session.flow().maskedEmail(),
                            config.messages().verifyInternalError()
                    ));
                }
            } finally {
                session.finishProcessing();
            }
        });
    }

    private void handleVerifyResend(AuthSession session, Audience audience) {
        AuthService service = authService;
        UUID accountId = session.flow().accountId();
        if (service == null || accountId == null) {
            audience.showDialog(dialogFactory.loginDialog(config.messages().verifyInternalError()));
            return;
        }
        if (!session.tryStartProcessing()) {
            return;
        }

        authExecutor.execute(() -> {
            try {
                var account = service.findAccount(accountId);
                if (account.isEmpty()) {
                    audience.showDialog(dialogFactory.loginDialog(config.messages().verifyInternalError()));
                    return;
                }
                EmailChallengeService.IssueResult issue = service.resendSignupCode(account.get());
                String masked = session.flow().maskedEmail();
                audience.showDialog(dialogFactory.verifyEmailDialog(masked, messageForIssue(issue, masked)));
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unexpected verify resend error", exception);
                if (session.isPending()) {
                    audience.showDialog(dialogFactory.verifyEmailDialog(
                            session.flow().maskedEmail(),
                            config.messages().verifyInternalError()
                    ));
                }
            } finally {
                session.finishProcessing();
            }
        });
    }

    private void handleForgotSendCode(
            AuthSession session,
            UUID profileId,
            Audience audience,
            @Nullable DialogResponseView view
    ) {
        AuthService service = authService;
        EmailChallengePurpose purpose = session.flow().purpose();
        if (service == null || purpose == null
                || (purpose != EmailChallengePurpose.PASSWORD_RESET
                && purpose != EmailChallengePurpose.USERNAME_RECOVERY)) {
            audience.showDialog(dialogFactory.forgotHubDialog(config.messages().forgotEmailInternalError()));
            return;
        }

        String email = textOrEmpty(view, AuthDialogKeys.INPUT_EMAIL);
        if (!service.isValidEmailFormat(email)) {
            audience.showDialog(dialogFactory.forgotEmailDialog(config.messages().forgotEmailInvalid()));
            return;
        }

        if (!session.tryStartProcessing()) {
            return;
        }

        authExecutor.execute(() -> {
            try {
                ForgotPasswordResult result = service.beginRecovery(email, purpose, profileId);
                applyForgotSendResult(session, audience, purpose, result);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unexpected forgot-send error for profile " + profileId, exception);
                if (session.isPending()) {
                    audience.showDialog(dialogFactory.forgotEmailDialog(config.messages().forgotEmailInternalError()));
                }
            } finally {
                session.finishProcessing();
            }
        });
    }

    private void handleForgotVerify(
            AuthSession session,
            UUID profileId,
            Audience audience,
            @Nullable DialogResponseView view
    ) {
        AuthService service = authService;
        UUID accountId = session.flow().accountId();
        EmailChallengePurpose purpose = session.flow().purpose();
        String masked = session.flow().maskedEmail();

        if (service == null || purpose == null) {
            audience.showDialog(dialogFactory.forgotHubDialog(config.messages().verifyInternalError()));
            return;
        }

        String code = textOrEmpty(view, AuthDialogKeys.INPUT_CODE);
        if (code.isBlank()) {
            audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().verifyEmptyCode()));
            return;
        }

        if (accountId == null) {
            // Enumeration-safe path: pretend invalid code when no account was found.
            audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().verifyInvalidCode()));
            return;
        }

        if (!session.tryStartProcessing()) {
            return;
        }

        authExecutor.execute(() -> {
            try {
                RecoverVerifyResult result = service.verifyRecoveryCode(accountId, purpose, code, profileId);
                applyRecoverVerifyResult(session, audience, result);
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unexpected forgot-verify error for profile " + profileId, exception);
                if (session.isPending()) {
                    audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().verifyInternalError()));
                }
            } finally {
                session.finishProcessing();
            }
        });
    }

    private void handleForgotResend(AuthSession session, Audience audience) {
        AuthService service = authService;
        UUID accountId = session.flow().accountId();
        EmailChallengePurpose purpose = session.flow().purpose();
        String masked = session.flow().maskedEmail();

        if (service == null || purpose == null) {
            audience.showDialog(dialogFactory.forgotHubDialog(config.messages().verifyInternalError()));
            return;
        }
        if (accountId == null) {
            audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().forgotEmailSent()));
            return;
        }
        if (!session.tryStartProcessing()) {
            return;
        }

        authExecutor.execute(() -> {
            try {
                var account = service.findAccount(accountId);
                if (account.isEmpty()) {
                    audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().forgotEmailSent()));
                    return;
                }
                EmailChallengeService.IssueResult issue = service.resendRecoveryCode(account.get(), purpose);
                audience.showDialog(dialogFactory.forgotVerifyDialog(masked, messageForIssue(issue, masked)));
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unexpected forgot-resend error", exception);
                if (session.isPending()) {
                    audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().verifyInternalError()));
                }
            } finally {
                session.finishProcessing();
            }
        });
    }

    private void handleResetPassword(
            AuthSession session,
            Audience audience,
            @Nullable DialogResponseView view
    ) {
        AuthService service = authService;
        UUID accountId = session.flow().accountId();
        if (service == null || accountId == null || !session.flow().passwordResetAuthorized()) {
            audience.showDialog(dialogFactory.loginDialog(config.messages().resetPasswordInternalError()));
            return;
        }

        String password = textOrEmpty(view, AuthDialogKeys.INPUT_PASSWORD);
        String confirm = textOrEmpty(view, AuthDialogKeys.INPUT_PASSWORD_CONFIRM);

        if (!session.tryStartProcessing()) {
            return;
        }

        authExecutor.execute(() -> {
            try {
                ResetPasswordResult result = service.resetPassword(accountId, password, confirm);
                switch (result) {
                    case ResetPasswordResult.Success ignored -> {
                        session.flow().showLogin();
                        audience.showDialog(dialogFactory.loginDialog(config.messages().resetPasswordSuccess()));
                    }
                    case ResetPasswordResult.ValidationError validationError ->
                            audience.showDialog(dialogFactory.resetPasswordDialog(validationError.message()));
                    case ResetPasswordResult.Unauthorized ignored ->
                            audience.showDialog(dialogFactory.loginDialog(config.messages().resetPasswordInternalError()));
                    case ResetPasswordResult.InternalError ignored ->
                            audience.showDialog(dialogFactory.resetPasswordDialog(
                                    config.messages().resetPasswordInternalError()));
                }
            } catch (Exception exception) {
                logger.log(Level.SEVERE, "Unexpected reset-password error", exception);
                if (session.isPending()) {
                    audience.showDialog(dialogFactory.resetPasswordDialog(
                            config.messages().resetPasswordInternalError()));
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
            case LoginResult.Success success -> admitAuthenticatedAccount(
                    session, profileId, audience, success.account(), true);
            case LoginResult.EmailNotVerified emailNotVerified -> {
                session.flow().beginEmailVerification(
                        emailNotVerified.account().id(),
                        emailNotVerified.maskedEmail()
                );
                String notice = emailNotVerified.codeSent()
                        ? null
                        : config.messages().signupEmailUnavailable();
                audience.showDialog(dialogFactory.verifyEmailDialog(emailNotVerified.maskedEmail(), notice));
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

    private void applySignupResult(AuthSession session, Audience audience, SignupResult result) {
        if (!session.isPending()) {
            return;
        }

        switch (result) {
            case SignupResult.PendingVerification pending -> {
                session.flow().beginEmailVerification(pending.account().id(), pending.maskedEmail());
                audience.showDialog(dialogFactory.verifyEmailDialog(pending.maskedEmail(), null));
            }
            case SignupResult.ValidationError validationError ->
                    audience.showDialog(dialogFactory.signupDialog(validationError.message()));
            case SignupResult.UsernameTaken ignored ->
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupUsernameTaken()));
            case SignupResult.EmailTaken ignored ->
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupEmailTaken()));
            case SignupResult.RateLimited ignored ->
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupRateLimited()));
            case SignupResult.EmailUnavailable ignored ->
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupEmailUnavailable()));
            case SignupResult.InternalError ignored ->
                    audience.showDialog(dialogFactory.signupDialog(config.messages().signupInternalError()));
        }
    }

    private void applyVerifyEmailResult(
            AuthSession session,
            UUID profileId,
            Audience audience,
            VerifyEmailResult result
    ) {
        if (!session.isPending()) {
            return;
        }

        String masked = session.flow().maskedEmail();
        switch (result) {
            case VerifyEmailResult.Success success -> admitAuthenticatedAccount(
                    session, profileId, audience, success.account(), false);
            case VerifyEmailResult.InvalidCode ignored ->
                    audience.showDialog(dialogFactory.verifyEmailDialog(masked, config.messages().verifyInvalidCode()));
            case VerifyEmailResult.Expired ignored ->
                    audience.showDialog(dialogFactory.verifyEmailDialog(masked, config.messages().verifyExpired()));
            case VerifyEmailResult.AttemptsExhausted ignored ->
                    audience.showDialog(dialogFactory.verifyEmailDialog(
                            masked, config.messages().verifyAttemptsExhausted()));
            case VerifyEmailResult.NoChallenge ignored ->
                    audience.showDialog(dialogFactory.verifyEmailDialog(masked, config.messages().verifyExpired()));
            case VerifyEmailResult.InternalError ignored ->
                    audience.showDialog(dialogFactory.verifyEmailDialog(
                            masked, config.messages().verifyInternalError()));
        }
    }

    private void applyForgotSendResult(
            AuthSession session,
            Audience audience,
            EmailChallengePurpose purpose,
            ForgotPasswordResult result
    ) {
        if (!session.isPending()) {
            return;
        }

        switch (result) {
            case ForgotPasswordResult.Accepted accepted -> {
                String masked = accepted.maskedEmailOrBlank().isBlank()
                        ? "***"
                        : accepted.maskedEmailOrBlank();
                session.flow().beginForgotVerify(accepted.accountIdOrNull(), purpose, masked);
                audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().forgotEmailSent()));
            }
            case ForgotPasswordResult.InvalidEmail ignored ->
                    audience.showDialog(dialogFactory.forgotEmailDialog(config.messages().forgotEmailInvalid()));
            case ForgotPasswordResult.RateLimited ignored ->
                    audience.showDialog(dialogFactory.forgotEmailDialog(config.messages().loginRateLimited()));
            case ForgotPasswordResult.EmailUnavailable ignored ->
                    audience.showDialog(dialogFactory.forgotEmailDialog(config.messages().forgotEmailUnavailable()));
            case ForgotPasswordResult.Cooldown cooldown ->
                    audience.showDialog(dialogFactory.forgotEmailDialog(
                            config.messages().verifyCooldown()
                                    .replace("{seconds}", Long.toString(cooldown.retryAfterSeconds()))));
            case ForgotPasswordResult.InternalError ignored ->
                    audience.showDialog(dialogFactory.forgotEmailDialog(config.messages().forgotEmailInternalError()));
        }
    }

    private void applyRecoverVerifyResult(AuthSession session, Audience audience, RecoverVerifyResult result) {
        if (!session.isPending()) {
            return;
        }

        String masked = session.flow().maskedEmail();
        switch (result) {
            case RecoverVerifyResult.PasswordResetAuthorized authorized -> {
                session.flow().authorizePasswordReset(authorized.account().id());
                audience.showDialog(dialogFactory.resetPasswordDialog(null));
            }
            case RecoverVerifyResult.UsernameRevealed revealed -> {
                session.flow().revealUsername(revealed.username());
                audience.showDialog(dialogFactory.usernameRevealDialog(revealed.username()));
            }
            case RecoverVerifyResult.InvalidCode ignored ->
                    audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().verifyInvalidCode()));
            case RecoverVerifyResult.Expired ignored ->
                    audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().verifyExpired()));
            case RecoverVerifyResult.AttemptsExhausted ignored ->
                    audience.showDialog(dialogFactory.forgotVerifyDialog(
                            masked, config.messages().verifyAttemptsExhausted()));
            case RecoverVerifyResult.NoChallenge ignored ->
                    audience.showDialog(dialogFactory.forgotVerifyDialog(masked, config.messages().verifyExpired()));
            case RecoverVerifyResult.InternalError ignored ->
                    audience.showDialog(dialogFactory.forgotVerifyDialog(
                            masked, config.messages().verifyInternalError()));
        }
    }

    /**
     * Registers the account session, preloads body state, then allows world join.
     *
     * @param loginDialogOnFailure when true, errors return to the login dialog; otherwise verify-email
     */
    private void admitAuthenticatedAccount(
            AuthSession session,
            UUID profileId,
            Audience audience,
            Account account,
            boolean loginDialogOnFailure
    ) {
        if (!session.isPending()) {
            return;
        }

        Optional<AccountSession> begun = accountSessions.tryBegin(account.id(), profileId, account.username());
        if (begun.isEmpty()) {
            showAuthFailure(session, audience, loginDialogOnFailure, config.messages().loginAlreadyOnline());
            logger.info("Rejected concurrent login for account '" + account.username()
                    + "' (minecraftUuid=" + profileId + ")");
            return;
        }

        AccountSession accountSession = begun.get();
        PlayerStateService states = playerStates;
        if (states == null) {
            accountSessions.endByMinecraftUuid(profileId);
            showAuthFailure(session, audience, loginDialogOnFailure, config.messages().loginInternalError());
            return;
        }

        try {
            AccountPlayerState preloaded = states.preload(account.id());
            accountSession.setPreloadedState(preloaded);
        } catch (Exception exception) {
            accountSessions.endByMinecraftUuid(profileId);
            logger.log(Level.SEVERE, "Failed to preload body for account '" + account.username() + "'", exception);
            showAuthFailure(session, audience, loginDialogOnFailure, config.messages().loginInternalError());
            return;
        }

        if (sessionManager.complete(profileId, AuthResult.ALLOWED)) {
            audience.closeDialog();
            logger.info("Admitted account '" + account.username()
                    + "' (minecraftUuid=" + profileId + ")");
        } else {
            accountSessions.endByMinecraftUuid(profileId);
        }
    }

    private void showAuthFailure(
            AuthSession session,
            Audience audience,
            boolean loginDialogOnFailure,
            String message
    ) {
        if (!session.isPending()) {
            return;
        }
        if (loginDialogOnFailure) {
            audience.showDialog(dialogFactory.loginDialog(message));
        } else {
            audience.showDialog(dialogFactory.verifyEmailDialog(session.flow().maskedEmail(), message));
        }
    }

    private String messageForIssue(EmailChallengeService.IssueResult issue, String maskedEmail) {
        return switch (issue) {
            case EmailChallengeService.IssueResult.Sent ignored ->
                    config.messages().verifyResent().replace("{email}", maskedEmail == null ? "***" : maskedEmail);
            case EmailChallengeService.IssueResult.Cooldown cooldown ->
                    config.messages().verifyCooldown()
                            .replace("{seconds}", Long.toString(cooldown.retryAfterSeconds()));
            case EmailChallengeService.IssueResult.EmailUnavailable ignored ->
                    config.messages().signupEmailUnavailable();
            case EmailChallengeService.IssueResult.InternalError ignored ->
                    config.messages().verifyInternalError();
        };
    }

    private static String textOrEmpty(@Nullable DialogResponseView view, String key) {
        if (view == null) {
            return "";
        }
        String value = view.getText(key);
        return value == null ? "" : value;
    }
}
