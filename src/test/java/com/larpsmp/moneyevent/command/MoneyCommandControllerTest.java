package com.larpsmp.moneyevent.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.larpsmp.moneyevent.auth.DevelopmentConsoleOpAuthorizer;
import com.larpsmp.moneyevent.auth.MoneyAdminAction;
import com.larpsmp.moneyevent.auth.MoneyAdminAuthorizer;
import com.larpsmp.moneyevent.combat.AttributionCause;
import com.larpsmp.moneyevent.combat.CombatKillRecord;
import com.larpsmp.moneyevent.combat.JdbcCombatKillRepository;
import com.larpsmp.moneyevent.display.BalanceDisplayControl;
import com.larpsmp.moneyevent.notification.PaymentNotificationStore;
import com.larpsmp.moneyevent.team.JdbcTeamRepository;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamColor;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.TestDatabaseSupport;
import com.larpsmp.moneyevent.wallet.TransactionRecord;
import com.larpsmp.moneyevent.wallet.TransactionType;
import java.nio.file.Path;
import java.time.Instant;
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
            assertTrue(missing.lastMessage().contains("do not have a wallet"));

            FakeSource player = registered(context, "Amira");
            context.controller().balance(player, new String[0]);
            assertEquals("Your balance: $200", player.lastMessage());
        }
    }

    @Test
    void balanceTargetAndLookupSupportOfflineCaseInsensitiveNamesWithoutCreatingUnknowns() throws Exception {
        try (Context context = context()) {
            FakeSource target = registered(context, "PlayerName");
            context.online().remove(target.playerId().orElseThrow());
            FakeSource console = FakeSource.console(true);

            context.controller().balance(console, new String[] {target.name().toLowerCase()});
            assertEquals(target.name() + "'s balance: $200", console.lastMessage());
            int walletsBefore = context.money().registeredAccounts().size();
            context.controller().balance(console, new String[] {"unknown"});
            assertTrue(console.lastMessage().contains("does not have a registered wallet"));
            assertEquals(walletsBefore, context.money().registeredAccounts().size());
        }
    }

    @Test
    void successfulPayTransfersMessagesOnlineRecipientAndRecordsActorAndReason() throws Exception {
        try (Context context = context()) {
            FakeSource sender = registered(context, "SenderName");
            FakeSource recipient = registered(context, "PlayerName");
            UUID senderId = sender.playerId().orElseThrow();
            UUID recipientId = recipient.playerId().orElseThrow();

            context.controller().pay(sender, new String[] {recipient.name(), "50"});

            assertEquals("You sent " + recipient.name() + " $50. Your balance is now $150.", sender.lastMessage());
            assertEquals(sender.name() + " sent you $50. Your balance is now $250.",
                    context.online().lastMessage(recipientId));
            assertEquals(150, context.money().balance(senderId).balance());
            assertEquals(250, context.money().balance(recipientId).balance());
            String expectedReason = "Player payment from " + senderId + " to " + recipientId;
            TransactionRecord payment = context.money().allTransactions().stream()
                    .filter(record -> record.type() == TransactionType.TRANSFER)
                    .filter(record -> expectedReason.equals(record.reason()))
                    .findFirst().orElseThrow();
            assertEquals(context.money().account(senderId).orElseThrow().accountId(), payment.actorId());
            assertEquals(expectedReason, payment.reason());
        }
    }

    @Test
    void offlinePayQueuesNotificationAndDeliversOnceOnJoin() throws Exception {
        try (Context context = context()) {
            FakeSource sender = registered(context, "SenderName");
            FakeSource recipient = registered(context, "OfflineName");
            UUID recipientId = recipient.playerId().orElseThrow();
            context.online().remove(recipientId);

            context.controller().pay(sender, new String[] {recipient.name(), "25"});
            assertEquals(175, context.money().balance(sender.playerId().orElseThrow()).balance());
            assertEquals(225, context.money().balance(recipientId).balance());

            List<String> delivered = new ArrayList<>();
            context.notifications().deliver(recipientId, delivered::add);
            assertEquals(1, delivered.size());
            assertTrue(delivered.getFirst().contains(sender.name() + " sent you $25"));
            assertTrue(delivered.getFirst().contains("$225"));

            List<String> second = new ArrayList<>();
            context.notifications().deliver(recipientId, second::add);
            assertTrue(second.isEmpty());
        }
    }

    @Test
    void payRejectsMissingWalletsSelfPayAndInvalidAmounts() throws Exception {
        try (Context context = context()) {
            FakeSource sender = registered(context, "Sender");
            FakeSource offline = registered(context, "OfflinePlayer");
            UUID offlineId = offline.playerId().orElseThrow();
            context.online().remove(offlineId);

            FakeSource missing = FakeSource.player(UUID.randomUUID(), "Missing");
            context.online().add(missing.playerId().orElseThrow(), "Missing");
            context.controller().pay(missing, new String[] {sender.name(), "1"});
            assertTrue(missing.lastMessage().contains("do not have a wallet"));

            context.controller().pay(sender, new String[] {sender.name(), "1"});
            assertTrue(sender.lastMessage().contains("cannot pay yourself"));

            context.controller().pay(sender, new String[] {offline.name(), "0"});
            assertTrue(sender.lastMessage().contains("positive whole dollars"));
            context.controller().pay(sender, new String[] {offline.name(), "201"});
            assertTrue(sender.lastMessage().contains("You only have $200"));
        }
    }

    @Test
    void adminGiveTakeSetRequireReasonAndPersistAudit() throws Exception {
        try (Context context = context()) {
            FakeSource target = registered(context, "Target");
            UUID targetId = target.playerId().orElseThrow();
            FakeSource console = FakeSource.console(true);
            long refreshesBefore = context.display().refreshes().stream().filter(targetId::equals).count();

            context.controller().larp(console, new String[] {"money", "give", target.name(), "25", "Event", "bonus"});
            assertEquals(225, context.money().balance(targetId).balance());
            context.controller().larp(console, new String[] {"money", "take", target.name(), "10", "Fee"});
            assertEquals(215, context.money().balance(targetId).balance());
            context.controller().larp(console, new String[] {"money", "set", target.name(), "50", "Reset"});
            assertEquals(50, context.money().balance(targetId).balance());

            TransactionRecord give = context.money().allTransactions().stream()
                    .filter(item -> item.type() == TransactionType.ADD && item.reason().equals("Event bonus"))
                    .findFirst().orElseThrow();
            assertEquals("MONEY_GIVE", give.authorizationAction());
            assertEquals("CONSOLE", give.senderType());
            assertEquals(refreshesBefore + 3,
                    context.display().refreshes().stream().filter(targetId::equals).count());
        }
    }

    @Test
    void adminInvalidAmountsDoNotMutateBalance() throws Exception {
        try (Context context = context()) {
            FakeSource target = registered(context, "Target");
            UUID targetId = target.playerId().orElseThrow();
            FakeSource console = FakeSource.console(true);
            int refreshes = context.display().refreshes().size();
            for (String invalid : List.of("0", "-1", "1.5", "bad", "9223372036854775808")) {
                context.controller().larp(console,
                        new String[] {"money", "give", target.name(), invalid, "Invalid", "test"});
            }
            context.controller().larp(console, new String[] {"money", "take", target.name(), "201", "Too", "much"});
            context.controller().larp(console, new String[] {"money", "set", target.name(), "-1", "Negative"});
            context.controller().larp(console, new String[] {"money", "give", target.name(), "1"});
            context.controller().larp(console, new String[] {"money", "give", "Unknown", "1", "No wallet"});
            assertEquals(200, context.money().balance(targetId).balance());
            assertEquals(refreshes, context.display().refreshes().size());
            TransactionRecord failed = context.money().allTransactions().stream()
                    .filter(item -> item.reason().equals("Too much")).findFirst().orElseThrow();
            assertFalse(failed.failureReason().isBlank());
            assertEquals("CONSOLE", failed.senderType());
        }
    }

    @Test
    void authorizerReceivesEachActionAndHidesDeniedCompletions() throws Exception {
        List<MoneyAdminAction> checked = new ArrayList<>();
        MoneyAdminAuthorizer spy = (source, action) -> {
            checked.add(action);
            return false;
        };
        try (Context context = context(spy)) {
            FakeSource player = FakeSource.player(UUID.randomUUID(), "ordinary");
            for (String operation : List.of("give", "take", "set")) {
                context.controller().larp(player, new String[] {"money", operation, "x"});
            }
            assertTrue(checked.containsAll(List.of(
                    MoneyAdminAction.MONEY_GIVE, MoneyAdminAction.MONEY_TAKE, MoneyAdminAction.MONEY_SET)));
            assertTrue(context.controller().tabComplete("larp", player, new String[] {"money", ""}).isEmpty());
        }
    }

    @Test
    void removedCommandsAreRejected() throws Exception {
        try (Context context = context()) {
            FakeSource console = FakeSource.console(true);
            context.controller().larp(console, new String[] {"money", "initialize", "Someone"});
            assertTrue(console.lastMessage().contains("Unknown protected money operation"));
            context.controller().larp(console, new String[] {"money", "display", "off"});
            assertTrue(console.lastMessage().contains("Unknown protected money operation"));
            context.controller().larp(console, new String[] {"money", "account", "create", "Someone"});
            assertTrue(console.lastMessage().contains("Unknown protected money operation"));
        }
    }

    @Test
    void tabCompleteListsGiveTakeSetOnly() throws Exception {
        try (Context context = context()) {
            FakeSource console = FakeSource.console(true);
            List<String> actions = context.controller().tabComplete("larp", console, new String[] {"money", ""});
            assertEquals(List.of("give", "set", "take"), actions);
        }
    }

    @Test
    void topKillsRanksNormalKillsAndExcludesEventAndFriendlyFire() throws Exception {
        try (Context context = contextWithKills()) {
            FakeSource killer = registered(context, "KillLead");
            FakeSource victim = registered(context, "KillVictim");
            FakeSource other = registered(context, "KillOther");
            UUID killerAccount = context.fixture().accountId(
                    new TestDatabaseSupport.RegisteredPlayer(killer.playerId().orElseThrow(), killer.name()));
            UUID victimAccount = context.fixture().accountId(
                    new TestDatabaseSupport.RegisteredPlayer(victim.playerId().orElseThrow(), victim.name()));
            UUID otherAccount = context.fixture().accountId(
                    new TestDatabaseSupport.RegisteredPlayer(other.playerId().orElseThrow(), other.name()));

            Instant now = Instant.parse("2026-06-01T12:00:00Z");
            JdbcCombatKillRepository kills = context.kills();
            kills.insert(new CombatKillRecord(
                    "top-kill-1", victimAccount, killerAccount, AttributionCause.MELEE, "sword",
                    false, false, "PAID", now));
            kills.insert(new CombatKillRecord(
                    "top-kill-2", otherAccount, killerAccount, AttributionCause.PROJECTILE, "arrow",
                    false, false, "PAID", now));
            kills.insert(new CombatKillRecord(
                    "top-kill-event", victimAccount, killerAccount, AttributionCause.MELEE, "event",
                    true, false, "PAID", now));
            kills.insert(new CombatKillRecord(
                    "top-kill-ff", killerAccount, victimAccount, AttributionCause.MELEE, "ff",
                    false, true, "FRIENDLY_FIRE", now));

            FakeSource console = FakeSource.console(true);
            context.controller().top(console, new String[] {"kills"});
            assertTrue(console.messages().stream().anyMatch(line -> line.contains("Kill standings")));
            assertTrue(console.messages().stream().anyMatch(line -> line.contains(killer.name()) && line.contains("2 kills")));
            assertTrue(console.messages().stream().noneMatch(line -> line.contains(victim.name())));
        }
    }

    @Test
    void topKillsUnavailableWithoutRepositoryAndUsageListsModes() throws Exception {
        try (Context context = context()) {
            FakeSource console = FakeSource.console(true);
            context.controller().top(console, new String[] {"kills"});
            assertEquals("Kill standings are not available.", console.lastMessage());
            context.controller().top(console, new String[] {});
            assertTrue(console.lastMessage().contains("Usage: /top <teams|money|kills>"));
            assertEquals(List.of("kills", "money", "teams"),
                    context.controller().tabComplete("top", console, new String[] {""}));
        }
    }

    @Test
    void topKillsReportsEmptyWhenNoEligibleRows() throws Exception {
        try (Context context = contextWithKills()) {
            try (var connection = context.fixture().dataSource().getConnection();
                 var statement = connection.prepareStatement("DELETE FROM combat_kills")) {
                statement.executeUpdate();
            }
            FakeSource console = FakeSource.console(true);
            context.controller().top(console, new String[] {"kills"});
            assertEquals("No kills recorded yet.", console.lastMessage());
        }
    }

    @Test
    void teamInfoShowsRosterAndRespectsVisibility() throws Exception {
        try (Context context = contextWithTeams()) {
            String teamName = "Disp" + Integer.toHexString(UUID.randomUUID().hashCode());
            Team team = context.teams().createTeam(teamName, TeamColor.AQUA).team();
            assertTrue(team != null);
            FakeSource player = registered(context, "Rostered");
            UUID accountId = context.fixture().accountId(
                    new TestDatabaseSupport.RegisteredPlayer(player.playerId().orElseThrow(), player.name()));
            assertTrue(context.teams().assignMember(accountId, team.id()).success());
            assertTrue(context.teams().setTier(accountId, PlayerTier.TIER_2).success());
            assertTrue(context.teams().setCaptain(team.id(), accountId).success());

            context.teamInfo().team(player, new String[0]);
            assertTrue(player.messages().stream().anyMatch(line -> line.contains(teamName) && line.contains("roster")));
            assertTrue(player.messages().stream().anyMatch(line -> line.contains("Team Money:")));
            assertTrue(player.messages().stream().anyMatch(line -> line.contains(player.name()) && line.contains("T2")));

            context.money().setTeamTotalsVisible(false, "test", "TEST", "hide");
            player.messages().clear();
            context.teamInfo().team(player, new String[] {"balance"});
            assertEquals("Team totals are currently hidden.", player.lastMessage());

            context.teams().deleteTeam(team.id());
            context.money().setTeamTotalsVisible(true, "test", "TEST", "restore");
        }
    }

    @Test
    void playerHelpListsCoreCommands() {
        FakeSource player = FakeSource.player(UUID.randomUUID(), "Helper");
        new PlayerHelpCommandController().help(player);
        assertTrue(player.messages().stream().anyMatch(line -> line.contains("/top <teams|money|kills>")));
        assertTrue(player.messages().stream().anyMatch(line -> line.contains("/team")));
    }

    private FakeSource registered(Context context, String name) throws Exception {
        TestDatabaseSupport.RegisteredPlayer player = context.fixture().registerCompetitor(name);
        context.online().add(player.minecraftUuid(), player.username());
        return FakeSource.player(player.minecraftUuid(), player.username());
    }

    private Context context() throws Exception {
        return context(new DevelopmentConsoleOpAuthorizer(), false, false);
    }

    private Context contextWithKills() throws Exception {
        return context(new DevelopmentConsoleOpAuthorizer(), true, false);
    }

    private Context contextWithTeams() throws Exception {
        return context(new DevelopmentConsoleOpAuthorizer(), true, true);
    }

    private Context context(MoneyAdminAuthorizer authorizer) throws Exception {
        return context(authorizer, false, false);
    }

    private Context context(MoneyAdminAuthorizer authorizer, boolean withKills, boolean withTeams) throws Exception {
        TestDatabaseSupport.Fixture fixture = TestDatabaseSupport.open("MoneyCommandControllerTest");
        assumeTrue(fixture != null, "No reachable Postgres for money command tests");
        MoneyService money = fixture.money();
        FakeOnlinePlayers online = new FakeOnlinePlayers();
        WalletPlayerLookup lookup = new WalletPlayerLookup(money, online);
        PaymentNotificationStore notifications = new PaymentNotificationStore(
                temporaryDirectory.resolve("notifications-" + UUID.randomUUID()), money, ignored -> { });
        FakeDisplay display = new FakeDisplay();
        money.addBalanceChangeListener((playerId, balance) -> display.refresh(playerId));
        JdbcCombatKillRepository kills = withKills ? new JdbcCombatKillRepository(fixture.dataSource()) : null;
        TeamService teams = null;
        TeamInfoCommandController teamInfo = null;
        if (withTeams) {
            teams = new TeamService(
                    new JdbcTeamRepository(fixture.dataSource()),
                    fixture.accounts(),
                    ignored -> { });
            money.setTeamService(teams);
            teamInfo = new TeamInfoCommandController(money, teams, ignored -> { });
        }
        MoneyCommandController controller = new MoneyCommandController(
                money, lookup, online, notifications, ignored -> { }, authorizer,
                null, teams, display, null, null, null, kills, fixture.accounts());
        return new Context(fixture, money, notifications, lookup, online, controller, display, kills, teams, teamInfo);
    }

    private record Context(
            TestDatabaseSupport.Fixture fixture,
            MoneyService money,
            PaymentNotificationStore notifications,
            WalletPlayerLookup lookup,
            FakeOnlinePlayers online,
            MoneyCommandController controller,
            FakeDisplay display,
            JdbcCombatKillRepository kills,
            TeamService teams,
            TeamInfoCommandController teamInfo) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            fixture.close();
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

        static FakeSource console(boolean permission) {
            return new FakeSource(null, "CONSOLE", permission);
        }

        @Override
        public Optional<UUID> playerId() {
            return Optional.ofNullable(playerId);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isConsole() {
            return playerId == null;
        }

        @Override
        public boolean isOperator() {
            return permission;
        }

        @Override
        public void send(String message, MessageKind ignored) {
            messages.add(message);
        }

        String lastMessage() {
            return messages.getLast();
        }

        List<String> messages() {
            return messages;
        }
    }

    private static final class FakeDisplay implements BalanceDisplayControl {
        private final List<UUID> refreshes = new ArrayList<>();

        @Override
        public void refresh(UUID playerId) {
            refreshes.add(playerId);
        }

        @Override
        public void refreshTeam(UUID teamId) {
        }

        @Override
        public void refreshAccounts(java.util.Collection<UUID> accountIds) {
        }

        @Override
        public void refreshPlayerAndTeammates(UUID playerId) {
            refresh(playerId);
        }

        @Override
        public void playerJoined(UUID playerId) {
        }

        @Override
        public void playerQuit(UUID playerId) {
        }

        @Override
        public void refreshAll() {
        }

        @Override
        public void close() {
        }

        List<UUID> refreshes() {
            return refreshes;
        }
    }

    private static final class FakeOnlinePlayers implements OnlinePlayerAccess {
        private final Map<UUID, String> online = new LinkedHashMap<>();
        private final Map<UUID, List<String>> messages = new LinkedHashMap<>();

        void add(UUID playerId, String name) {
            online.put(playerId, name);
        }

        void remove(UUID playerId) {
            online.remove(playerId);
        }

        String lastMessage(UUID playerId) {
            return messages.get(playerId).getLast();
        }

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

        @Override
        public boolean isOnline(UUID playerId) {
            return online.containsKey(playerId);
        }

        @Override
        public void send(UUID playerId, String message, MessageKind ignored) {
            messages.computeIfAbsent(playerId, key -> new ArrayList<>()).add(message);
        }

        @Override
        public void send(UUID playerId, net.kyori.adventure.text.Component message) {
            messages.computeIfAbsent(playerId, key -> new ArrayList<>())
                    .add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(message));
        }
    }
}
