package com.larpsmp.moneyevent;

import com.larpsmp.moneyevent.auction.AuctionCommandController;
import com.larpsmp.moneyevent.auction.AuctionConfig;
import com.larpsmp.moneyevent.auction.AuctionConfigWriter;
import com.larpsmp.moneyevent.auction.AuctionService;
import com.larpsmp.moneyevent.auction.BidCommandController;
import com.larpsmp.moneyevent.auction.JdbcAuctionRepository;
import com.larpsmp.moneyevent.auth.AccountAdminRepository;
import com.larpsmp.moneyevent.auth.AccountAdminService;
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
import com.larpsmp.moneyevent.capture.CaptureBlockListener;
import com.larpsmp.moneyevent.capture.CaptureBossBarPresenter;
import com.larpsmp.moneyevent.capture.CaptureCommandController;
import com.larpsmp.moneyevent.capture.CaptureConfig;
import com.larpsmp.moneyevent.capture.CaptureConfigWriter;
import com.larpsmp.moneyevent.capture.CaptureOccupancyScanner;
import com.larpsmp.moneyevent.capture.CaptureRewardNotifier;
import com.larpsmp.moneyevent.capture.CaptureService;
import com.larpsmp.moneyevent.capture.CaptureTeleportGuard;
import com.larpsmp.moneyevent.capture.CaptureTempBlockTracker;
import com.larpsmp.moneyevent.capture.CaptureTickTask;
import com.larpsmp.moneyevent.capture.JdbcCaptureRepository;
import com.larpsmp.moneyevent.combat.AttackMemory;
import com.larpsmp.moneyevent.combat.BlockOwnershipListener;
import com.larpsmp.moneyevent.combat.BlockOwnershipTracker;
import com.larpsmp.moneyevent.combat.CombatDamageListener;
import com.larpsmp.moneyevent.combat.EventCombatScope;
import com.larpsmp.moneyevent.combat.JdbcCombatKillRepository;
import com.larpsmp.moneyevent.combat.KillAttributionService;
import com.larpsmp.moneyevent.combat.KillDeathListener;
import com.larpsmp.moneyevent.command.BukkitMoneyCommands;
import com.larpsmp.moneyevent.spawn.SpawnBlockListener;
import com.larpsmp.moneyevent.spawn.SpawnCommandController;
import com.larpsmp.moneyevent.spawn.SpawnConfig;
import com.larpsmp.moneyevent.spawn.SpawnConfigWriter;
import com.larpsmp.moneyevent.spawn.SpawnProtectionListener;
import com.larpsmp.moneyevent.spawn.SpawnService;
import com.larpsmp.moneyevent.command.BukkitOnlinePlayerAccess;
import com.larpsmp.moneyevent.command.MessageKind;
import com.larpsmp.moneyevent.command.MoneyCommandController;
import com.larpsmp.moneyevent.command.MoneyJoinListener;
import com.larpsmp.moneyevent.command.PlayerHelpCommandController;
import com.larpsmp.moneyevent.command.TeamInfoCommandController;
import com.larpsmp.moneyevent.command.WalletPlayerLookup;
import com.larpsmp.moneyevent.config.EnvSettings;
import com.larpsmp.moneyevent.db.DataSourceFactory;
import com.larpsmp.moneyevent.db.MigrationRunner;
import com.larpsmp.moneyevent.display.BalanceDisplayControl;
import com.larpsmp.moneyevent.display.BukkitBalanceSidebar;
import com.larpsmp.moneyevent.display.TeamPresenceDisplay;
import com.larpsmp.moneyevent.email.EmailSender;
import com.larpsmp.moneyevent.email.ResendClient;
import com.larpsmp.moneyevent.notification.PaymentNotificationStore;
import com.larpsmp.moneyevent.playerstate.AccountIdentityBinder;
import com.larpsmp.moneyevent.playerstate.AccountPlayerState;
import com.larpsmp.moneyevent.playerstate.AccountPlayerStateRepository;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.playerstate.PlayerStateConfig;
import com.larpsmp.moneyevent.playerstate.PlayerStateListener;
import com.larpsmp.moneyevent.playerstate.PlayerStateService;
import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.TeamAdminDialogFactory;
import com.larpsmp.moneyevent.team.TeamAdminListener;
import com.larpsmp.moneyevent.team.TeamAdminSession;
import com.larpsmp.moneyevent.team.TeamAdminTool;
import com.larpsmp.moneyevent.team.TeamCommandController;
import com.larpsmp.moneyevent.team.TeamService;
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
    private TeamService teamService;
    private CaptureService captureService;
    private CaptureBossBarPresenter captureBossBar;
    private BalanceDisplayControl balanceDisplay;
    private AuthSessionManager authSessionManager;
    private AccountSessionManager accountSessionManager;
    private PlayerStateService playerStateService;
    private HikariDataSource dataSource;
    private ExecutorService authExecutor;
    private ScheduledExecutorService rateLimitCleanupExecutor;
    private BukkitTask autosaveTask;
    private CaptureTickTask captureTickTask;
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

        AccountIdentityBinder identityBinder = new AccountIdentityBinder(getLogger());
        boolean commandIndexReady = identityBinder.commandNames().warmUp();
        AuthConnectionListener authListener = new AuthConnectionListener(
                authConfig,
                authService,
                authSessionManager,
                accountSessionManager,
                playerStateService,
                identityBinder,
                dialogFactory,
                listenerExecutor,
                databaseReady,
                getLogger()
        );
        getServer().getPluginManager().registerEvents(authListener, this);

        if (authConfig.enabled() && databaseReady) {
            getLogger().info("Pre-join authentication enabled (database-backed).");
            if (commandIndexReady) {
                getLogger().info("Account identity binder ready (profile rename + vanilla command-name index).");
            } else {
                getLogger().severe("Account identity profile rename active, but vanilla command-name index "
                        + "could not be discovered — /gamemode|/tp may still need launcher nicknames.");
            }
        }

        if (!databaseReady || dataSource == null || accountRepository == null || playerStateService == null) {
            getLogger().severe("Money system requires a healthy PostgreSQL connection; money commands disabled.");
            getLogger().info("Money Event plugin enabled (auth-only until database is ready).");
            return;
        }

        try {
            getServer().getPluginManager().registerEvents(
                    new PlayerStateListener(
                            this, accountSessionManager, playerStateService, identityBinder, getLogger()),
                    this
            );
            startAutosave(playerStateService, playerStateConfig);

            final AccountRepository moneyAccounts = accountRepository;
            JdbcMoneyRepository moneyRepository = new JdbcMoneyRepository(dataSource);
            moneyService = new MoneyService(
                    moneyAccounts, moneyRepository, message -> getLogger().severe(message));
            JdbcTeamRepository teamRepository = new JdbcTeamRepository(dataSource);
            teamService = new TeamService(
                    teamRepository, moneyAccounts, message -> getLogger().severe(message));
            moneyService.setTeamService(teamService);
            teamService.setCompetitorEnrollmentListener(accountId -> {
                try {
                    boolean granted = moneyService.grantCompetitorStartingBalanceIfNeeded(accountId);
                    if (granted) {
                        getLogger().info("Granted competitor starting balance to account " + accountId + ".");
                    }
                } catch (IOException exception) {
                    getLogger().severe("Failed to grant competitor starting balance to " + accountId
                            + ": " + exception.getMessage());
                }
            });
            AccountAdminService accountAdmins = new AccountAdminService(
                    new AccountAdminRepository(dataSource),
                    moneyAccounts,
                    accountSessionManager,
                    message -> getLogger().severe(message));
            TeamAdminTool teamAdminTool = new TeamAdminTool(this);
            playerStateService.setCaptureExcludedItem(teamAdminTool::isTool);
            TeamAdminSession teamAdminSessions = new TeamAdminSession();
            TeamAdminDialogFactory teamDialogs = new TeamAdminDialogFactory(teamService);
            DevelopmentConsoleOpAuthorizer adminAuthorizer = new DevelopmentConsoleOpAuthorizer();
            TeamAdminListener teamAdminListener = new TeamAdminListener(
                    this,
                    teamService,
                    teamDialogs,
                    teamAdminSessions,
                    teamAdminTool,
                    adminAuthorizer,
                    accountAdmins
            );
            getServer().getPluginManager().registerEvents(teamAdminListener, this);
            TeamCommandController teamCommands = new TeamCommandController(
                    teamService, adminAuthorizer, player -> teamAdminListener.openHub(player, null));
            BukkitOnlinePlayerAccess onlinePlayers = new BukkitOnlinePlayerAccess(
                    getServer(), accountSessionManager);
            PaymentNotificationStore notifications = new PaymentNotificationStore(
                    getDataFolder().toPath().resolve("notifications"),
                    moneyService,
                    message -> getLogger().severe(message));
            BukkitBalanceSidebar sidebar = new BukkitBalanceSidebar(
                    getServer(),
                    moneyService,
                    teamService,
                    moneyAccounts,
                    message -> getLogger().severe(message));
            balanceDisplay = sidebar;
            TeamPresenceDisplay teamPresence = new TeamPresenceDisplay(
                    getServer(),
                    teamService,
                    moneyAccounts,
                    sidebar,
                    message -> getLogger().severe(message));
            JdbcCombatKillRepository killRepository = new JdbcCombatKillRepository(dataSource);
            // Rankings are global: any balance change must refresh every viewer's stacked HUD.
            moneyService.addBalanceChangeListener(
                    (playerId, ignoredBalance) -> balanceDisplay.refreshAll());
            moneyService.addSettlementNoticeListener((accountId, message) -> {
                try {
                    moneyAccounts.findIdentityByAccountId(accountId).ifPresent(identity -> {
                        if (onlinePlayers.isOnline(identity.minecraftUuid())) {
                            onlinePlayers.send(identity.minecraftUuid(), message, MessageKind.INFO);
                        }
                    });
                } catch (java.sql.SQLException exception) {
                    getLogger().severe("Could not notify account " + accountId + " of settlement: "
                            + exception.getMessage());
                }
            });
            teamService.addRosterChangeListener((teamIds, accountIds) -> {
                // invalidate + repaint rankings for all; personal Team Money follows roster too
                balanceDisplay.refreshAll();
                teamPresence.refreshAll();
            });

            CaptureConfig captureConfig = CaptureConfig.from(getConfig());
            JdbcCaptureRepository captureRepository = new JdbcCaptureRepository(dataSource);
            captureService = new CaptureService(
                    captureConfig,
                    captureRepository,
                    moneyService,
                    teamService,
                    message -> getLogger().severe(message));
            CaptureTempBlockTracker tempBlocks = new CaptureTempBlockTracker(
                    captureRepository,
                    java.time.Clock.systemUTC(),
                    message -> getLogger().severe(message));
            CaptureBossBarPresenter bossBar = new CaptureBossBarPresenter(teamService);
            captureBossBar = bossBar;
            CaptureOccupancyScanner occupancy = new CaptureOccupancyScanner(
                    accountSessionManager,
                    moneyAccounts,
                    teamService,
                    accountAdmins::isAdminMinecraftUuid,
                    message -> getLogger().severe(message));
            CaptureCommandController captureCommands = new CaptureCommandController(
                    captureService,
                    updated -> CaptureConfigWriter.writePoints(this, updated),
                    () -> {
                        reloadConfig();
                        captureService.replaceConfig(CaptureConfig.from(getConfig()));
                    });
            CaptureRewardNotifier captureRewardNotifier = new CaptureRewardNotifier(
                    moneyAccounts,
                    teamService,
                    onlinePlayers,
                    message -> getLogger().severe(message));
            captureService.addListener(new CaptureService.CaptureEventListener() {
                @Override
                public void onBroadcast(String message) {
                    CaptureTickTask.broadcast(message);
                }

                @Override
                public void onOpened(com.larpsmp.moneyevent.capture.CaptureSession session,
                        com.larpsmp.moneyevent.capture.CapturePointDefinition point) {
                    // Boss bar updated on next tick.
                }

                @Override
                public void onStopped(com.larpsmp.moneyevent.capture.CaptureSession session) {
                    bossBar.clear();
                }

                @Override
                public void onCompleted(
                        com.larpsmp.moneyevent.capture.CaptureSession session,
                        UUID winningTeamId,
                        com.larpsmp.moneyevent.wallet.SplitRewardResult payout) {
                    var point = captureService.config().point(session.pointId()).orElse(null);
                    captureRewardNotifier.notifyCompletion(session, point, winningTeamId, payout);
                }
            });
            captureService.restoreFromDatabase();
            tempBlocks.restoreAllFromDatabase();

            SpawnService spawnService = new SpawnService(SpawnConfig.from(getConfig()));
            SpawnCommandController spawnCommands = new SpawnCommandController(
                    spawnService,
                    updated -> SpawnConfigWriter.write(this, updated),
                    () -> {
                        reloadConfig();
                        spawnService.replaceConfig(SpawnConfig.from(getConfig()));
                    });

            AuctionConfig auctionConfig = AuctionConfig.from(getConfig());
            JdbcAuctionRepository auctionRepository = new JdbcAuctionRepository(dataSource);
            AuctionService auctionService = new AuctionService(
                    auctionConfig,
                    auctionRepository,
                    moneyService,
                    teamService,
                    moneyAccounts,
                    message -> getLogger().severe(message));
            auctionService.setConfigPersister(updated -> AuctionConfigWriter.write(this, updated));
            auctionService.addListener(message -> CaptureTickTask.broadcast(message));
            moneyService.addTransferSuccessListener(auctionService::onTransferSuccess);
            auctionService.restoreFromDatabase();
            // Keep config.yml aligned with durable DB payee after restore.
            AuctionConfigWriter.write(this, auctionService.config());
            WalletPlayerLookup walletLookup = new WalletPlayerLookup(moneyService, onlinePlayers);
            AuctionCommandController auctionCommands = new AuctionCommandController(
                    auctionService,
                    moneyService,
                    walletLookup,
                    message -> getLogger().severe(message));
            BidCommandController bidCommands = new BidCommandController(
                    auctionService,
                    moneyService,
                    message -> getLogger().severe(message));

            MoneyCommandController controller = new MoneyCommandController(
                    moneyService,
                    walletLookup,
                    onlinePlayers,
                    notifications,
                    message -> getLogger().severe(message),
                    adminAuthorizer,
                    teamCommands,
                    teamService,
                    balanceDisplay,
                    accountAdmins,
                    captureCommands,
                    spawnCommands,
                    auctionCommands,
                    killRepository,
                    moneyAccounts);
            TeamInfoCommandController teamInfoCommands = new TeamInfoCommandController(
                    moneyService, teamService, message -> getLogger().severe(message));
            PlayerHelpCommandController playerHelp = new PlayerHelpCommandController();
            BukkitMoneyCommands commands = new BukkitMoneyCommands(
                    controller, teamInfoCommands, playerHelp, accountAdmins, bidCommands);
            for (String commandName : new String[] {"balance", "pay", "top", "team", "help", "bid", "larp"}) {
                var command = Objects.requireNonNull(getCommand(commandName),
                        "Command missing from plugin.yml: " + commandName);
                command.setExecutor(commands);
                command.setTabCompleter(commands);
            }
            getServer().getPluginManager().registerEvents(
                    new MoneyJoinListener(
                            moneyService,
                            notifications,
                            message -> getLogger().severe(message),
                            balanceDisplay,
                            accountSessionManager),
                    this);

            AttackMemory attackMemory = new AttackMemory();
            BlockOwnershipTracker blockOwnership = new BlockOwnershipTracker();
            KillAttributionService killAttribution = new KillAttributionService(
                    attackMemory,
                    accountSessionManager,
                    moneyAccounts,
                    teamService,
                    moneyService,
                    killRepository,
                    EventCombatScope.NEVER,
                    message -> getLogger().severe(message)
            );
            getServer().getPluginManager().registerEvents(new BlockOwnershipListener(blockOwnership), this);
            getServer().getPluginManager().registerEvents(
                    new CombatDamageListener(attackMemory, blockOwnership), this);
            getServer().getPluginManager().registerEvents(
                    new KillDeathListener(killAttribution, moneyAccounts, onlinePlayers, getLogger()), this);

            getServer().getPluginManager().registerEvents(
                    new CaptureBlockListener(captureService, tempBlocks), this);
            getServer().getPluginManager().registerEvents(new CaptureTeleportGuard(this), this);
            getServer().getPluginManager().registerEvents(
                    new org.bukkit.event.Listener() {
                        @org.bukkit.event.EventHandler
                        public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
                            bossBar.showTo(event.getPlayer());
                        }
                    }, this);
            captureTickTask = new CaptureTickTask(captureService, occupancy, bossBar, tempBlocks);
            captureTickTask.start(this);

            getServer().getPluginManager().registerEvents(new SpawnProtectionListener(spawnService), this);
            getServer().getPluginManager().registerEvents(new SpawnBlockListener(spawnService), this);

            getLogger().info("Plugin admins are LarpSMP-account based. Grant with: /larp op <username>");
            getLogger().info("Account-linked wallets ready (competitor starting balance $"
                    + MoneyService.STARTING_BALANCE + " on first team assignment).");
            getLogger().info("Economy settlement APIs ready (split/death/rescue/kill-money).");
            getLogger().info("Kill attribution ready (60s last-attacker window → death settlement).");
            getLogger().info("Capture points ready (/larp capture start|pause|resume|stop|define).");
            getLogger().info("Auctions ready (/larp auction …, /bid; payments to "
                    + auctionService.config().paymentUsername() + ").");
            getLogger().info("Spawn protection ready (/larp spawn define|status|reload; "
                    + spawnService.config().protectionSeconds() + "s after respawn in region).");
            getLogger().info("Teams administration ready (dialog UI + /larp team|captain|tier|teams).");
            getLogger().info("Display ready (sidebar, team nametags/tab grouping, /top kills, /team, /help).");
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
        if (captureTickTask != null) {
            captureTickTask.cancel();
            captureTickTask = null;
        }
        if (captureService != null) {
            captureService.flush();
            captureService = null;
        }
        if (captureBossBar != null) {
            captureBossBar.clear();
            captureBossBar = null;
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
        teamService = null;
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

    public TeamService getTeamService() {
        if (teamService == null) {
            throw new IllegalStateException("Team service is not available");
        }
        return teamService;
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
