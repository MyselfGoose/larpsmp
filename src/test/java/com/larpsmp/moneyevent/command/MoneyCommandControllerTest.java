package com.larpsmp.moneyevent.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.larpsmp.moneyevent.notification.PaymentNotificationStore;
import com.larpsmp.moneyevent.wallet.FileTransactionStore;
import com.larpsmp.moneyevent.wallet.FileWalletRepository;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.TransactionRecord;
import com.larpsmp.moneyevent.wallet.TransactionType;
import com.larpsmp.moneyevent.wallet.WalletService;
import com.larpsmp.moneyevent.display.BalanceDisplayControl;
import com.larpsmp.moneyevent.auth.DevelopmentConsoleOpAuthorizer;
import com.larpsmp.moneyevent.auth.MoneyAdminAction;
import com.larpsmp.moneyevent.auth.MoneyAdminAuthorizer;
import com.larpsmp.moneyevent.display.DisplaySettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MoneyCommandControllerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void balanceShowsOwnBalanceAndDoesNotCreateMissingWallet() throws Exception {
        try (Context context = context()) {
            FakeSource missing = FakeSource.player(UUID.randomUUID(), "missing");
            context.controller().balance(missing, new String[0]);
            assertTrue(missing.lastMessage().contains("not been initialized"));
            assertEquals(0, context.money().registeredAccounts().size());

            FakeSource player = initialized(context, "Amira");
            context.controller().balance(player, new String[0]);
            assertEquals("Your balance: $200", player.lastMessage());
        }
    }

    @Test
    void balanceTargetAndLookupSupportOfflineCaseInsensitiveNamesWithoutCreatingUnknowns() throws Exception {
        try (Context context = context()) {
            FakeSource target = initialized(context, "PlayerName");
            context.online().remove(target.playerId().orElseThrow());
            FakeSource console = FakeSource.console(true);

            context.controller().balance(console, new String[] {"playername"});
            assertEquals("PlayerName's balance: $200", console.lastMessage());
            int walletsBefore = context.money().registeredAccounts().size();
            context.controller().balance(console, new String[] {"unknown"});
            assertTrue(console.lastMessage().contains("does not have a registered wallet"));
            assertEquals(walletsBefore, context.money().registeredAccounts().size());
        }
    }

    @Test
    void duplicateStoredUsernameFailsClearly() throws Exception {
        try (Context context = context()) {
            context.wallets().getOrCreate(UUID.randomUUID(), "Duplicate");
            context.wallets().getOrCreate(UUID.randomUUID(), "duplicate");
            PlayerLookupResult result = context.lookup().resolve("DUPLICATE");
            assertEquals(PlayerLookupResult.Status.AMBIGUOUS, result.status());

            FakeSource console = FakeSource.console(true);
            context.controller().balance(console, new String[] {"duplicate"});
            assertTrue(console.lastMessage().contains("multiple registered wallets"));
        }
    }

    @Test
    void successfulPayTransfersMessagesOnlineRecipientAndRecordsActorAndReason() throws Exception {
        try (Context context = context()) {
            FakeSource sender = initialized(context, "SenderName");
            FakeSource recipient = initialized(context, "PlayerName");
            UUID senderId = sender.playerId().orElseThrow();
            UUID recipientId = recipient.playerId().orElseThrow();

            context.controller().pay(sender, new String[] {"PlayerName", "50"});

            assertEquals("You sent PlayerName $50. Your balance is now $150.", sender.lastMessage());
            assertEquals("SenderName sent you $50. Your balance is now $250.",
                    context.online().lastMessage(recipientId));
            assertEquals(150, context.money().balance(senderId).balance());
            assertEquals(250, context.money().balance(recipientId).balance());
            TransactionRecord payment = context.transactions().loadAll().stream()
                    .filter(record -> record.type() == TransactionType.TRANSFER)
                    .findFirst().orElseThrow();
            assertEquals(senderId, payment.actorId());
            assertEquals("Player payment from " + senderId + " to " + recipientId, payment.reason());
        }
    }

    @Test
    void offlinePayPersistsOneNotificationAndFailedPayCreatesNone() throws Exception {
        UUID senderId;
        UUID recipientId;
        try (Context context = context()) {
            FakeSource sender = initialized(context, "SenderName");
            FakeSource recipient = initialized(context, "OfflineName");
            senderId = sender.playerId().orElseThrow();
            recipientId = recipient.playerId().orElseThrow();
            context.online().remove(recipientId);

            context.controller().pay(sender, new String[] {"OfflineName", "50"});
            assertFalse(context.online().messages().containsKey(recipientId));
        }

        try (Context reopened = context()) {
            List<String> delivered = new ArrayList<>();
            reopened.notifications().deliver(recipientId, delivered::add);
            assertEquals(List.of("While you were offline, SenderName sent you $50. Your balance is now $250."),
                    delivered);
            reopened.notifications().deliver(recipientId, delivered::add);
            assertEquals(1, delivered.size());

            FakeSource sender = FakeSource.player(senderId, "SenderName");
            reopened.controller().pay(sender, new String[] {"OfflineName", "1000"});
            reopened.notifications().deliver(recipientId, delivered::add);
            assertEquals(1, delivered.size());
        }
    }

    @Test
    void tabCompletionUsesNamesAndHidesProtectedCommandsFromUnauthorizedPlayers() throws Exception {
        try (Context context = context()) {
            FakeSource sender = initialized(context, "Sender");
            FakeSource offline = initialized(context, "OfflinePlayer");
            context.online().remove(offline.playerId().orElseThrow());

            assertTrue(context.controller().tabComplete("balance", sender, new String[] {"off"})
                    .contains("OfflinePlayer"));
            assertFalse(context.controller().tabComplete("pay", sender, new String[] {""})
                    .contains("Sender"));
            assertTrue(context.controller().tabComplete("larp", sender, new String[] {""}).isEmpty());
            assertEquals(List.of("money"), context.controller().tabComplete(
                    "larp", FakeSource.console(true), new String[] {""}));
        }
    }

    @Test
    void payRejectsInvalidAmountsInsufficientFundsSelfAndUnknownRecipient() throws Exception {
        try (Context context = context()) {
            FakeSource sender = initialized(context, "Sender");
            for (String invalid : List.of("0", "-1", "1.5", "nope", "9223372036854775808")) {
                context.controller().pay(sender, new String[] {"Nobody", invalid});
                assertEquals("Amounts must be positive whole dollars.", sender.lastMessage());
            }
            context.controller().pay(sender, new String[] {"Sender", "1"});
            assertEquals("You cannot pay yourself.", sender.lastMessage());
            context.controller().pay(sender, new String[] {"Nobody", "1"});
            assertTrue(sender.lastMessage().contains("does not have a registered wallet"));
            FakeSource recipient = initialized(context, "Recipient");
            context.controller().pay(sender, new String[] {"Recipient", "201"});
            assertEquals("You only have $200.", sender.lastMessage());
            assertEquals(200, context.money().balance(sender.playerId().orElseThrow()).balance());
            assertEquals(200, context.money().balance(recipient.playerId().orElseThrow()).balance());
        }
    }

    @Test
    void payRejectsMissingSenderWalletAndConsole() throws Exception {
        try (Context context = context()) {
            FakeSource recipient = initialized(context, "Recipient");
            FakeSource missing = FakeSource.player(UUID.randomUUID(), "Missing");
            context.online().add(missing.playerId().orElseThrow(), "Missing");
            context.controller().pay(missing, new String[] {"Recipient", "1"});
            assertTrue(missing.lastMessage().contains("not been initialized"));

            FakeSource console = FakeSource.console(true);
            context.controller().pay(console, new String[] {"Recipient", "1"});
            assertEquals("Only players can use /pay.", console.lastMessage());
            context.controller().balance(console, new String[] {"Recipient"});
            assertEquals("Recipient's balance: $200", console.lastMessage());
        }
    }

    @Test
    void initializationRequiresPermissionAndExactOnlinePlayerAndIsIdempotent() throws Exception {
        try (Context context = context()) {
            UUID targetId = UUID.randomUUID();
            context.online().add(targetId, "Target");
            FakeSource unauthorized = FakeSource.player(UUID.randomUUID(), "ordinary");
            context.controller().larp(unauthorized, new String[] {"money", "initialize", "Target"});
            assertTrue(unauthorized.lastMessage().contains("not authorized"));
            assertFalse(context.money().balance(targetId).successful());

            FakeSource console = FakeSource.console(true);
            context.controller().larp(console, new String[] {"money", "initialize", "target"});
            assertTrue(console.lastMessage().contains("not online with that exact name"));
            context.controller().larp(console, new String[] {"money", "initialize", "Target"});
            assertTrue(console.lastMessage().startsWith("Initialized Target"));
            context.controller().larp(console, new String[] {"money", "initialize", "Target"});
            assertTrue(console.lastMessage().contains("already initialized"));
            assertTrue(console.lastMessage().contains("$200"));
            assertEquals(200, context.money().balance(targetId).balance());
        }
    }

    @Test
    void joinMetadataUpdateDoesNotCreateOrInitializeWallet() throws Exception {
        try (Context context = context()) {
            UUID unknown = UUID.randomUUID();
            assertTrue(context.money().updateUsernameIfRegistered(unknown, "NewName").isEmpty());
            assertFalse(context.money().balance(unknown).successful());

            UUID existing = UUID.randomUUID();
            context.wallets().getOrCreate(existing, "OldName");
            context.money().updateUsernameIfRegistered(existing, "NewName");
            assertEquals("NewName", context.money().account(existing).orElseThrow().lastKnownUsername());
            assertFalse(context.money().account(existing).orElseThrow().initialized());
            assertEquals(0, context.money().account(existing).orElseThrow().balance());
        }
    }

    @Test
    void developmentAuthorizerAllowsConsoleAndOperatorsButRejectsOrdinaryPlayers() {
        DevelopmentConsoleOpAuthorizer authorizer = new DevelopmentConsoleOpAuthorizer();
        assertTrue(authorizer.isAllowed(FakeSource.console(true), MoneyAdminAction.MONEY_GIVE));
        assertTrue(authorizer.isAllowed(FakeSource.operator(UUID.randomUUID(), "op"), MoneyAdminAction.MONEY_SET));
        assertFalse(authorizer.isAllowed(FakeSource.player(UUID.randomUUID(), "ordinary"),
                MoneyAdminAction.MONEY_ACCOUNT_CREATE));
    }

    @Test
    void accountCreationCreatesZeroNonCompetitorAndNeverResetsExistingWallet() throws Exception {
        try (Context context = context()) {
            UUID targetId = UUID.randomUUID();
            context.online().add(targetId, "Organizer");
            FakeSource console = FakeSource.console(true);
            long recordsBefore = context.transactions().loadAll().size();
            context.controller().larp(console, new String[] {"money", "account", "create", "Organizer"});
            assertEquals("Created a $0 wallet for Organizer.", console.lastMessage());
            assertEquals(0, context.money().account(targetId).orElseThrow().balance());
            assertFalse(context.money().account(targetId).orElseThrow().initialized());
            assertEquals(recordsBefore, context.transactions().loadAll().size());
            context.money().add(targetId, 75, "setup", null);
            context.controller().larp(console, new String[] {"money", "account", "create", "Organizer"});
            assertTrue(console.lastMessage().contains("Current balance: $75"));
            assertEquals(75, context.money().balance(targetId).balance());
        }
    }

    @Test
    void unauthorizedAccountCreationAndOfflineArbitraryNameDoNotCreateWallets() throws Exception {
        try (Context context = context()) {
            UUID targetId = UUID.randomUUID();
            context.online().add(targetId, "Target");
            FakeSource ordinary = FakeSource.player(UUID.randomUUID(), "ordinary");
            context.controller().larp(ordinary, new String[] {"money", "account", "create", "Target"});
            assertTrue(ordinary.lastMessage().contains("not authorized"));
            assertFalse(context.money().balance(targetId).successful());
            FakeSource console = FakeSource.console(true);
            context.controller().larp(console, new String[] {"money", "account", "create", "Unknown"});
            assertTrue(console.lastMessage().contains("not online"));
            assertTrue(context.money().registeredAccounts().isEmpty());
        }
    }

    @Test
    void adminGiveTakeSetPreserveReasonAuditIdentityAndUpdateDisplay() throws Exception {
        try (Context context = context()) {
            FakeSource target = initialized(context, "Target");
            UUID targetId = target.playerId().orElseThrow();
            FakeSource operator = FakeSource.operator(UUID.randomUUID(), "AmiraAdmin");
            context.controller().larp(operator,
                    new String[] {"money", "give", "Target", "100", "Auction", "testing"});
            assertEquals(300, context.money().balance(targetId).balance());
            context.controller().larp(operator,
                    new String[] {"money", "take", "Target", "25", "Correction", "after", "reward"});
            assertEquals(275, context.money().balance(targetId).balance());
            context.controller().larp(operator,
                    new String[] {"money", "set", "Target", "0", "Exact", "reset"});
            assertEquals(0, context.money().balance(targetId).balance());
            TransactionRecord record = context.transactions().loadAll().stream()
                    .filter(item -> item.reason().equals("Auction testing")).findFirst().orElseThrow();
            assertEquals(MoneyAdminAction.MONEY_GIVE.name(), record.authorizationAction());
            assertEquals("/larp money give", record.command());
            assertEquals(operator.playerId().orElseThrow(), record.actorId());
            assertEquals("AmiraAdmin", record.actorUsername());
            assertEquals("PLAYER", record.senderType());
            assertEquals(targetId, record.sourceWalletId());
            assertEquals(200, record.sourceBalanceBefore());
            assertEquals(300, record.sourceBalanceAfter());
            assertTrue(context.display().refreshes().stream().filter(targetId::equals).count() >= 3);
        }
    }

    @Test
    void adminValidationAndInsufficientTakeLeaveBalanceAndDisplayUnchanged() throws Exception {
        try (Context context = context()) {
            FakeSource target = initialized(context, "Target");
            UUID targetId = target.playerId().orElseThrow();
            FakeSource console = FakeSource.console(true);
            int refreshes = context.display().refreshes().size();
            for (String invalid : List.of("0", "-1", "1.5", "bad", "9223372036854775808")) {
                context.controller().larp(console,
                        new String[] {"money", "give", "Target", invalid, "Invalid", "test"});
            }
            context.controller().larp(console, new String[] {"money", "take", "Target", "201", "Too", "much"});
            context.controller().larp(console, new String[] {"money", "set", "Target", "-1", "Negative"});
            context.controller().larp(console, new String[] {"money", "give", "Target", "1"});
            context.controller().larp(console, new String[] {"money", "give", "Unknown", "1", "No wallet"});
            assertEquals(200, context.money().balance(targetId).balance());
            assertEquals(refreshes, context.display().refreshes().size());
            TransactionRecord failed = context.transactions().loadAll().stream()
                    .filter(item -> item.reason().equals("Too much")).findFirst().orElseThrow();
            assertFalse(failed.failureReason().isBlank());
            assertEquals("CONSOLE", failed.senderType());
        }
    }

    @Test
    void authorizerReceivesEachActionAndHidesDeniedCompletions() throws Exception {
        List<MoneyAdminAction> checked = new ArrayList<>();
        MoneyAdminAuthorizer spy = (source, action) -> { checked.add(action); return false; };
        try (Context context = context(spy)) {
            FakeSource player = FakeSource.player(UUID.randomUUID(), "ordinary");
            for (String operation : List.of("give", "take", "set", "initialize", "display")) {
                context.controller().larp(player, new String[] {"money", operation, "x"});
            }
            context.controller().larp(player, new String[] {"money", "account", "create", "x"});
            assertTrue(checked.containsAll(List.of(MoneyAdminAction.values())));
            assertTrue(context.controller().tabComplete("larp", player, new String[] {"money", ""}).isEmpty());
        }
    }

    @Test
    void displayToggleIsIdempotentAndSettingsPersistWithSafeCorruptionFallback() throws Exception {
        Path settingFile = temporaryDirectory.resolve("display.properties");
        DisplaySettings defaults = new DisplaySettings(settingFile, ignored -> { });
        assertTrue(defaults.enabled());
        defaults.setEnabled(false);
        assertFalse(new DisplaySettings(settingFile, ignored -> { }).enabled());
        java.nio.file.Files.writeString(settingFile, "enabled=corrupt\n");
        List<String> warnings = new ArrayList<>();
        assertTrue(new DisplaySettings(settingFile, warnings::add).enabled());
        assertFalse(warnings.isEmpty());

        try (Context context = context()) {
            FakeSource console = FakeSource.console(true);
            context.controller().larp(console, new String[] {"money", "display", "off"});
            assertFalse(context.display().isEnabled());
            context.controller().larp(console, new String[] {"money", "display", "off"});
            assertTrue(console.lastMessage().contains("already disabled"));
            context.controller().larp(console, new String[] {"money", "display", "on"});
            assertTrue(context.display().isEnabled());
        }
    }

    private FakeSource initialized(Context context, String name) {
        UUID playerId = UUID.randomUUID();
        context.online().add(playerId, name);
        FakeSource source = FakeSource.player(playerId, name);
        context.controller().larp(FakeSource.console(true), new String[] {"money", "initialize", name});
        return source;
    }

    private Context context() throws IOException {
        return context(new DevelopmentConsoleOpAuthorizer());
    }

    private Context context(MoneyAdminAuthorizer authorizer) throws IOException {
        FileWalletRepository repository = new FileWalletRepository(temporaryDirectory.resolve("wallets"));
        FileTransactionStore transactions =
                new FileTransactionStore(repository, temporaryDirectory.resolve("transactions"));
        WalletService wallets = new WalletService(repository);
        MoneyService money = new MoneyService(wallets, transactions, ignored -> { });
        FakeOnlinePlayers online = new FakeOnlinePlayers();
        WalletPlayerLookup lookup = new WalletPlayerLookup(money, online);
        PaymentNotificationStore notifications = new PaymentNotificationStore(
                temporaryDirectory.resolve("notifications"), money, ignored -> { });
        FakeDisplay display = new FakeDisplay();
        money.addBalanceChangeListener((playerId, balance) -> display.refresh(playerId));
        MoneyCommandController controller = new MoneyCommandController(
                money, lookup, online, notifications, ignored -> { }, authorizer, display);
        return new Context(wallets, money, transactions, notifications, lookup, online, controller, display);
    }

    private record Context(
            WalletService wallets,
            MoneyService money,
            FileTransactionStore transactions,
            PaymentNotificationStore notifications,
            WalletPlayerLookup lookup,
            FakeOnlinePlayers online,
            MoneyCommandController controller,
            FakeDisplay display) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            money.close();
        }
    }

    private static final class FakeSource implements CommandSource {
        private final UUID playerId;
        private final String name;
        private final boolean permission;
        private final List<String> messages = new ArrayList<>();

        private FakeSource(UUID playerId, String name, boolean permission) {
            this.playerId = playerId;
            this.name = name;
            this.permission = permission;
        }

        static FakeSource player(UUID playerId, String name) {
            return new FakeSource(playerId, name, false);
        }

        static FakeSource operator(UUID playerId, String name) {
            return new FakeSource(playerId, name, true);
        }

        static FakeSource console(boolean permission) {
            return new FakeSource(null, "CONSOLE", permission);
        }

        @Override public Optional<UUID> playerId() { return Optional.ofNullable(playerId); }
        @Override public String name() { return name; }
        @Override public boolean isConsole() { return playerId == null; }
        @Override public boolean isOperator() { return permission; }
        @Override public void send(String message, MessageKind ignored) { messages.add(message); }
        String lastMessage() { return messages.getLast(); }
    }

    private static final class FakeDisplay implements BalanceDisplayControl {
        private boolean enabled = true;
        private final List<UUID> refreshes = new ArrayList<>();
        @Override public boolean isEnabled() { return enabled; }
        @Override public boolean setEnabled(boolean enabled) {
            boolean changed = this.enabled != enabled;
            this.enabled = enabled;
            return changed;
        }
        @Override public void refresh(UUID playerId) { refreshes.add(playerId); }
        @Override public void playerJoined(UUID playerId) { }
        @Override public void playerQuit(UUID playerId) { }
        @Override public void close() { }
        List<UUID> refreshes() { return refreshes; }
    }

    private static final class FakeOnlinePlayers implements OnlinePlayerAccess {
        private final Map<UUID, String> online = new LinkedHashMap<>();
        private final Map<UUID, List<String>> messages = new LinkedHashMap<>();

        void add(UUID playerId, String name) { online.put(playerId, name); }
        void remove(UUID playerId) { online.remove(playerId); }
        Map<UUID, List<String>> messages() { return messages; }
        String lastMessage(UUID playerId) { return messages.get(playerId).getLast(); }

        @Override
        public List<OnlinePlayerIdentity> onlinePlayers() {
            return online.entrySet().stream()
                    .map(entry -> new OnlinePlayerIdentity(entry.getKey(), entry.getValue())).toList();
        }

        @Override
        public Optional<OnlinePlayerIdentity> findExact(String username) {
            return online.entrySet().stream().filter(entry -> entry.getValue().equals(username))
                    .map(entry -> new OnlinePlayerIdentity(entry.getKey(), entry.getValue())).findFirst();
        }

        @Override public boolean isOnline(UUID playerId) { return online.containsKey(playerId); }
        @Override public void send(UUID playerId, String message, MessageKind ignored) {
            messages.computeIfAbsent(playerId, key -> new ArrayList<>()).add(message);
        }
    }
}
