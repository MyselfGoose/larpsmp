package com.larpsmp.moneyevent;

import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.auth.AuthConfig;
import com.larpsmp.moneyevent.auth.AuthConnectionListener;
import com.larpsmp.moneyevent.auth.AuthDialogFactory;
import com.larpsmp.moneyevent.auth.AuthRateLimiter;
import com.larpsmp.moneyevent.auth.AuthService;
import com.larpsmp.moneyevent.auth.AuthSessionManager;
import com.larpsmp.moneyevent.auth.DevelopmentConsoleOpAuthorizer;
import com.larpsmp.moneyevent.auth.EmailChallengeRepository;
import com.larpsmp.moneyevent.auth.EmailChallengeService;
import com.larpsmp.moneyevent.auth.PasswordHasher;
import com.larpsmp.moneyevent.auth.VerificationCodeHasher;
import com.larpsmp.moneyevent.command.BukkitMoneyCommands;
import com.larpsmp.moneyevent.command.BukkitOnlinePlayerAccess;
import com.larpsmp.moneyevent.command.MoneyCommandController;
import com.larpsmp.moneyevent.command.MoneyJoinListener;
import com.larpsmp.moneyevent.command.WalletPlayerLookup;
import com.larpsmp.moneyevent.config.EnvSettings;
import com.larpsmp.moneyevent.db.DataSourceFactory;
import com.larpsmp.moneyevent.db.MigrationRunner;
import com.larpsmp.moneyevent.display.BalanceDisplayControl;
import com.larpsmp.moneyevent.display.BukkitBalanceSidebar;
import com.larpsmp.moneyevent.email.EmailSender;
import com.larpsmp.moneyevent.email.ResendClient;
import com.larpsmp.moneyevent.notification.PaymentNotificationStore;
import com.larpsmp.moneyevent.playerstate.AccountPlayerState;
import com.larpsmp.moneyevent.playerstate.AccountPlayerStateRepository;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.playerstate.PlayerStateConfig;
import com.larpsmp.moneyevent.playerstate.PlayerStateListener;
import com.larpsmp.moneyevent.playerstate.PlayerStateService;
import com.larpsmp.moneyevent.wallet.JdbcMoneyRepository;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

public final class MoneyEventPlugin extends JavaPlugin {

    private MoneyService moneyService;
    private BalanceDisplayControl balanceDisplay;
    private AuthSessionManager authSessionManager;
    private AccountSessionManager accountSessionManager;
    private PlayerStateService playerStateService;
    private HikariDataSource dataSource;
    private ExecutorService authExecutor;
    private ScheduledExecutorService rateLimitCleanupExecutor;
    private BukkitTask autosaveTask;
    private boolean databaseReady;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        EnvSettings env = EnvSettings.load();
        env.loadedFrom().ifPresentOrElse(
                path -> getLogger().info("Loaded secrets from " + path.toAbsolutePath() + "."),
                () -> getLogger().info("No project .env found; using process env / config.yml fallbacks.")
        );
        AuthConfig authConfig = AuthConfig.from(getConfig(), env);
        PlayerStateConfig playerStateConfig = PlayerStateConfig.from(getConfig());
        authSessionManager = new AuthSessionManager();
        accountSessionManager = new AccountSessionManager();
        AuthDialogFactory dialogFactory = new AuthDialogFactory(authConfig.messages());

        AuthService authService = null;
        AccountRepository accountRepository = null;
        playerStateService = null;
        databaseReady = false;

        if (authConfig.enabled()) {
            try {
                dataSource = DataSourceFactory.create(authConfig.database());
                try (var connection = dataSource.getConnection()) {
                    connection.isValid(5);
                }
                new MigrationRunner(dataSource, getLogger()).migrate();

                PasswordHasher passwordHasher = new PasswordHasher();
                accountRepository = new AccountRepository(dataSource);
                EmailChallengeRepository challengeRepository = new EmailChallengeRepository(dataSource);
                AuthRateLimiter rateLimiter = new AuthRateLimiter(authConfig.rateLimit());

                EmailSender emailSender = null;
                boolean emailConfigured = authConfig.integrations().resendConfigured();
                if (emailConfigured) {
                    emailSender = new ResendClient(
                            authConfig.integrations().resendApiKey(),
                            authConfig.integrations().resendFromEmail(),
                            getLogger()
                    );
                    getLogger().info("Resend email delivery configured (from "
                            + authConfig.integrations().resendFromEmail() + ").");
                } else {
                    getLogger().warning("Resend is not fully configured (need LARPSMP_RESEND_API_KEY and "
                            + "LARPSMP_RESEND_FROM_EMAIL). Email verification and recovery will be unavailable.");
                }

                VerificationCodeHasher codeHasher = new VerificationCodeHasher(
                        authConfig.integrations().resolveEmailCodePepper(),
                        authConfig.email().codeLength()
                );
                EmailChallengeService emailChallengeService = new EmailChallengeService(
                        challengeRepository,
                        accountRepository,
                        emailSender,
                        codeHasher,
                        authConfig.email(),
                        emailConfigured,
                        getLogger()
                );

                authService = new AuthService(
                        accountRepository,
                        passwordHasher,
                        rateLimiter,
                        emailChallengeService,
                        authConfig.signup(),
                        authConfig.messages(),
                        getLogger()
                );

                AccountPlayerStateRepository stateRepository = new AccountPlayerStateRepository(dataSource);
                playerStateService = new PlayerStateService(stateRepository, playerStateConfig, getLogger());

                AtomicInteger threadCounter = new AtomicInteger();
                authExecutor = Executors.newFixedThreadPool(
                        authConfig.database().poolSize(),
                        runnable -> {
                            Thread thread = new Thread(runnable, "larpsmp-auth-" + threadCounter.incrementAndGet());
                            thread.setDaemon(true);
                            return thread;
                        }
                );

                rateLimitCleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "larpsmp-auth-rate-limit-cleanup");
                    thread.setDaemon(true);
                    return thread;
                });
                rateLimitCleanupExecutor.scheduleAtFixedRate(
                        rateLimiter::purgeExpired,
                        60L,
                        60L,
                        TimeUnit.SECONDS
                );

                databaseReady = true;
                getLogger().info("PostgreSQL authentication ready ("
                        + authConfig.database().jdbcUrl() + ").");
                if (authConfig.integrations().storageConfigured()) {
                    getLogger().info("Remote storage credentials present in environment.");
                }
            } catch (Exception exception) {
                getLogger().log(
                        java.util.logging.Level.SEVERE,
                        "Failed to initialize authentication database: " + exception.getMessage(),
                        exception
                );
                getLogger().severe("Players will be denied until the database is available.");
                closeDataSourceQuietly();
                databaseReady = false;
                authService = null;
                accountRepository = null;
                playerStateService = null;
            }
        } else {
            getLogger().info("Pre-join authentication disabled.");
        }

        ExecutorService listenerExecutor = authExecutor != null
                ? authExecutor
                : Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "larpsmp-auth-fallback");
                    thread.setDaemon(true);
                    return thread;
                });
        if (authExecutor == null) {
            authExecutor = listenerExecutor;
        }

        AuthConnectionListener authListener = new AuthConnectionListener(
                authConfig,
                authService,
                authSessionManager,
                accountSessionManager,
                playerStateService,
                dialogFactory,
                listenerExecutor,
                databaseReady,
                getLogger()
        );
        getServer().getPluginManager().registerEvents(authListener, this);

        if (authConfig.enabled() && databaseReady) {
            getLogger().info("Pre-join authentication enabled (database-backed).");
        }

        if (!databaseReady || dataSource == null || accountRepository == null || playerStateService == null) {
            getLogger().severe("Money system requires a healthy PostgreSQL connection; money commands disabled.");
            getLogger().info("Money Event plugin enabled (auth-only until database is ready).");
            return;
        }

        try {
            getServer().getPluginManager().registerEvents(
                    new PlayerStateListener(accountSessionManager, playerStateService, getLogger()),
                    this
            );
            startAutosave(playerStateService, playerStateConfig);

            JdbcMoneyRepository moneyRepository = new JdbcMoneyRepository(dataSource);
            moneyService = new MoneyService(
                    accountRepository, moneyRepository, message -> getLogger().severe(message));
            BukkitOnlinePlayerAccess onlinePlayers = new BukkitOnlinePlayerAccess(
                    getServer(), accountSessionManager);
            PaymentNotificationStore notifications = new PaymentNotificationStore(
                    getDataFolder().toPath().resolve("notifications"),
                    moneyService,
                    message -> getLogger().severe(message));
            balanceDisplay = new BukkitBalanceSidebar(
                    getServer(), moneyService, message -> getLogger().severe(message));
            moneyService.addBalanceChangeListener((playerId, ignoredBalance) -> balanceDisplay.refresh(playerId));
            MoneyCommandController controller = new MoneyCommandController(
                    moneyService,
                    new WalletPlayerLookup(moneyService, onlinePlayers),
                    onlinePlayers,
                    notifications,
                    message -> getLogger().severe(message),
                    new DevelopmentConsoleOpAuthorizer());
            BukkitMoneyCommands commands = new BukkitMoneyCommands(controller);
            for (String commandName : new String[] {"balance", "pay", "larp"}) {
                var command = Objects.requireNonNull(getCommand(commandName),
                        "Command missing from plugin.yml: " + commandName);
                command.setExecutor(commands);
                command.setTabCompleter(commands);
            }
            getServer().getPluginManager().registerEvents(
                    new MoneyJoinListener(moneyService, notifications,
                            message -> getLogger().severe(message), balanceDisplay), this);
            getLogger().warning("Using temporary console/operator money authorizer; replace it with Azeem's adapter.");
            getLogger().info("Account-linked wallets ready (starting balance $"
                    + MoneyService.STARTING_BALANCE + ").");
            getLogger().info("Account-keyed player bodies enabled (autosave "
                    + playerStateConfig.autosaveSeconds() + "s).");
        } catch (IOException exception) {
            getLogger().severe("Could not initialize money storage: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Money Event plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }

        if (playerStateService != null && accountSessionManager != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                accountSessionManager.findByMinecraftUuid(player.getUniqueId()).ifPresent(session -> {
                    if (playerStateService.savePlayer(player, session.accountId())) {
                        playerStateService.clearVanillaShell(player);
                        getLogger().info("Flushed account body for '" + session.username()
                                + "' on disable.");
                    }
                });
            }
            accountSessionManager.clear();
        }

        if (balanceDisplay != null) {
            balanceDisplay.close();
            balanceDisplay = null;
        }
        if (moneyService != null) {
            moneyService.close();
            moneyService = null;
        }
        if (authSessionManager != null) {
            authSessionManager.cancelAll();
            authSessionManager = null;
        }
        playerStateService = null;
        accountSessionManager = null;
        shutdownExecutor(authExecutor, "auth executor");
        authExecutor = null;
        shutdownExecutor(rateLimitCleanupExecutor, "rate-limit cleanup executor");
        rateLimitCleanupExecutor = null;
        closeDataSourceQuietly();
        getLogger().info("Money Event plugin disabled.");
    }

    public MoneyService getMoneyService() {
        if (moneyService == null) {
            throw new IllegalStateException("Money service is not available");
        }
        return moneyService;
    }

    public AccountSessionManager getAccountSessionManager() {
        if (accountSessionManager == null) {
            throw new IllegalStateException("Account session manager is not available");
        }
        return accountSessionManager;
    }

    private void startAutosave(PlayerStateService states, PlayerStateConfig config) {
        int seconds = config.autosaveSeconds();
        if (seconds <= 0) {
            getLogger().info("Player-state autosave disabled.");
            return;
        }
        long periodTicks = seconds * 20L;
        autosaveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                AccountSession session = accountSessionManager.findByMinecraftUuid(player.getUniqueId())
                        .orElse(null);
                if (session == null || !session.isActive()) {
                    continue;
                }
                UUIDAccountSave save = captureForAutosave(player, session, states);
                if (save == null) {
                    continue;
                }
                authExecutor.execute(() -> {
                    AccountSession still = accountSessionManager.findByMinecraftUuid(save.minecraftUuid())
                            .orElse(null);
                    if (still == null || !still.accountId().equals(save.accountId())) {
                        return;
                    }
                    try {
                        states.save(save.state());
                    } catch (Exception exception) {
                        getLogger().log(
                                java.util.logging.Level.WARNING,
                                "Autosave failed for account '" + save.username() + "'",
                                exception
                        );
                    }
                });
            }
        }, periodTicks, periodTicks);
    }

    private record UUIDAccountSave(
            UUID minecraftUuid,
            UUID accountId,
            String username,
            AccountPlayerState state
    ) {
    }

    private @Nullable UUIDAccountSave captureForAutosave(
            Player player,
            AccountSession session,
            PlayerStateService states
    ) {
        try {
            return new UUIDAccountSave(
                    player.getUniqueId(),
                    session.accountId(),
                    session.username(),
                    states.capture(player, session.accountId())
            );
        } catch (Exception exception) {
            getLogger().log(
                    java.util.logging.Level.WARNING,
                    "Autosave capture failed for '" + session.username() + "'",
                    exception
            );
            return null;
        }
    }

    private void closeDataSourceQuietly() {
        if (dataSource != null) {
            try {
                dataSource.close();
            } catch (Exception exception) {
                getLogger().warning("Error closing datasource: " + exception.getMessage());
            }
            dataSource = null;
        }
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                getLogger().warning("Timed out waiting for " + name + " to stop.");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            getLogger().warning("Interrupted while shutting down " + name + ".");
        }
    }
}
