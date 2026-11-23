package com.larpsmp.moneyevent.auction;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.MoneyFormats;
import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Auction session lifecycle, fixed representatives, bidding, payment proof, and fallback.
 */
public final class AuctionService {

    private final Object lock = new Object();
    private final JdbcAuctionRepository repository;
    private final MoneyService money;
    private final TeamService teams;
    private final AccountRepository accounts;
    private final Clock clock;
    private final Consumer<String> errorLogger;
    private final List<AuctionEventListener> listeners = new ArrayList<>();

    private AuctionConfig config;
    private @Nullable AuctionSession activeSession;
    private @Nullable AuctionLot activeLot;

    public AuctionService(
            AuctionConfig config,
            JdbcAuctionRepository repository,
            MoneyService money,
            TeamService teams,
            AccountRepository accounts,
            Consumer<String> errorLogger) {
        this(config, repository, money, teams, accounts, Clock.systemUTC(), errorLogger);
    }

    public AuctionService(
            AuctionConfig config,
            JdbcAuctionRepository repository,
            MoneyService money,
            TeamService teams,
            AccountRepository accounts,
            Clock clock,
            Consumer<String> errorLogger) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.money = Objects.requireNonNull(money, "money");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public void addListener(AuctionEventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public AuctionConfig config() {
        return config;
    }

    public void replaceConfig(AuctionConfig updated) {
        synchronized (lock) {
            this.config = Objects.requireNonNull(updated, "updated");
        }
    }

    public Optional<AuctionSession> activeSession() {
        synchronized (lock) {
            return Optional.ofNullable(activeSession);
        }
    }

    public Optional<AuctionLot> activeLot() {
        synchronized (lock) {
            return Optional.ofNullable(activeLot);
        }
    }

    public void restoreFromDatabase() {
        synchronized (lock) {
            try {
                JdbcAuctionRepository.RuntimeState runtime = repository.loadRuntime();
                if (runtime.activeSessionId() == null) {
                    activeSession = null;
                    activeLot = null;
                    return;
                }
                Optional<AuctionSession> loaded = repository.findSession(runtime.activeSessionId());
                if (loaded.isEmpty() || !loaded.get().isOpen()) {
                    activeSession = null;
                    activeLot = null;
                    repository.setRuntime(null, null, clock.instant());
                    return;
                }
                activeSession = loaded.get();
                if (runtime.activeLotId() == null) {
                    activeLot = null;
                    return;
                }
                Optional<AuctionLot> lot = repository.findLot(runtime.activeLotId());
                if (lot.isEmpty() || lot.get().status().clearsRuntimePointer()) {
                    activeLot = null;
                    repository.setRuntime(activeSession.sessionId(), null, clock.instant());
                    return;
                }
                activeLot = lot.get();
            } catch (SQLException exception) {
                errorLogger.accept("Failed to restore auction runtime: " + exception.getMessage());
                activeSession = null;
                activeLot = null;
            }
        }
    }

    public SessionStartResult startSession() {
        synchronized (lock) {
            if (activeSession != null && activeSession.isOpen()) {
                return SessionStartResult.alreadyActive(activeSession);
            }
            Instant now = clock.instant();
            AuctionSession session = AuctionSession.start(UUID.randomUUID(), now);
            try {
                repository.upsertSession(session);
                repository.setRuntime(session.sessionId(), null, now);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to start auction session: " + exception.getMessage());
                return SessionStartResult.failed(exception.getMessage());
            }
            activeSession = session;
            activeLot = null;
            broadcast("[Auction] Session started. Register one representative per team.");
            return SessionStartResult.started(session);
        }
    }

    public RepResult registerRepresentative(UUID accountId) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.isOpen()) {
                return RepResult.noSession();
            }
            if (repsLocked()) {
                return RepResult.locked();
            }
            Optional<Team> team = teams.teamOf(accountId);
            if (team.isEmpty()) {
                return RepResult.notOnTeam();
            }
            Instant now = clock.instant();
            AuctionRepresentative representative = new AuctionRepresentative(
                    activeSession.sessionId(), team.get().id(), accountId, now);
            try {
                List<AuctionRepresentative> existing = repository.listRepresentatives(activeSession.sessionId());
                for (AuctionRepresentative current : existing) {
                    if (current.accountId().equals(accountId) && !current.teamId().equals(team.get().id())) {
                        return RepResult.alreadyOtherTeam();
                    }
                }
                repository.upsertRepresentative(representative);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to register auction representative: " + exception.getMessage());
                return RepResult.failed(exception.getMessage());
            }
            String username = resolveUsername(accountId);
            broadcast("[Auction] " + team.get().displayName() + " representative: " + username + ".");
            return RepResult.ok(representative, team.get());
        }
    }

    public OpenLotResult openLot(String theme, long startingBid) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.isOpen()) {
                return OpenLotResult.noSession();
            }
            if (activeLot != null && activeLot.status().blocksNewLot()) {
                return OpenLotResult.lotBusy(activeLot);
            }
            if (startingBid < 0) {
                return OpenLotResult.invalidStartingBid();
            }
            String trimmedTheme = theme == null ? "" : theme.trim();
            if (trimmedTheme.isEmpty()) {
                return OpenLotResult.invalidTheme();
            }
            // Only teams with rostered members need a representative (ignore empty seeded teams).
            List<Team> competingTeams = new ArrayList<>();
            for (Team team : teams.listTeams()) {
                if (!teams.membersOf(team.id()).isEmpty()) {
                    competingTeams.add(team);
                }
            }
            if (competingTeams.isEmpty()) {
                return OpenLotResult.missingReps(List.of());
            }
            List<AuctionRepresentative> reps;
            try {
                reps = repository.listRepresentatives(activeSession.sessionId());
            } catch (SQLException exception) {
                errorLogger.accept("Failed to load auction representatives: " + exception.getMessage());
                return OpenLotResult.failed(exception.getMessage());
            }
            Set<UUID> repTeams = new HashSet<>();
            for (AuctionRepresentative rep : reps) {
                repTeams.add(rep.teamId());
            }
            List<String> missing = new ArrayList<>();
            for (Team team : competingTeams) {
                if (!repTeams.contains(team.id())) {
                    missing.add(team.displayName());
                }
            }
            if (!missing.isEmpty()) {
                return OpenLotResult.missingReps(missing);
            }

            Instant now = clock.instant();
            int lotNumber;
            try {
                lotNumber = repository.nextLotNumber(activeSession.sessionId());
            } catch (SQLException exception) {
                errorLogger.accept("Failed to allocate lot number: " + exception.getMessage());
                return OpenLotResult.failed(exception.getMessage());
            }
            AuctionLot lot = AuctionLot.open(
                    UUID.randomUUID(), activeSession.sessionId(), lotNumber, trimmedTheme, startingBid, now);
            if (activeSession.status() == AuctionSessionStatus.SETUP) {
                activeSession.markActive(now);
            }
            try {
                repository.upsertSession(activeSession);
                repository.upsertLot(lot);
                repository.setRuntime(activeSession.sessionId(), lot.lotId(), now);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to open auction lot: " + exception.getMessage());
                return OpenLotResult.failed(exception.getMessage());
            }
            activeLot = lot;
            broadcast("[Auction] Lot " + lot.lotNumber() + ": \"" + lot.theme() + "\". Starting bid: "
                    + MoneyFormats.currency(lot.startingBid()) + ".");
            return OpenLotResult.opened(lot);
        }
    }

    public AdminResult countdown() {
        synchronized (lock) {
            if (activeLot == null || activeLot.status() != AuctionLotStatus.OPEN) {
                return AdminResult.fail("No open lot to start countdown on.");
            }
            Instant now = clock.instant();
            activeLot.beginCountdown(now);
            try {
                repository.upsertLot(activeLot);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to start auction countdown: " + exception.getMessage());
                return AdminResult.fail(exception.getMessage());
            }
            broadcast("[Auction] Final countdown — no new bids.");
            return AdminResult.ok("Countdown started.");
        }
    }

    public CloseLotResult closeLot() {
        synchronized (lock) {
            if (activeLot == null
                    || (activeLot.status() != AuctionLotStatus.OPEN
                    && activeLot.status() != AuctionLotStatus.COUNTDOWN)) {
                return CloseLotResult.noOpenLot();
            }
            Instant now = clock.instant();
            if (activeLot.highBidderAccountId() == null || activeLot.highBid() == null) {
                activeLot.markUnsold(now);
                try {
                    repository.upsertLot(activeLot);
                    repository.setRuntime(activeSession == null ? null : activeSession.sessionId(), null, now);
                } catch (SQLException exception) {
                    errorLogger.accept("Failed to close unsold lot: " + exception.getMessage());
                    return CloseLotResult.failed(exception.getMessage());
                }
                AuctionLot closed = activeLot;
                activeLot = null;
                broadcast("[Auction] Lot " + closed.lotNumber() + " closed with no bids (unsold).");
                return CloseLotResult.unsold(closed);
            }

            UUID winnerAccountId = activeLot.highBidderAccountId();
            long amount = activeLot.highBid();
            activeLot.closeAwaitingPayment(winnerAccountId, amount, now);
            try {
                repository.upsertLot(activeLot);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to close auction lot: " + exception.getMessage());
                return CloseLotResult.failed(exception.getMessage());
            }
            String teamName = teamDisplayNameForAccount(winnerAccountId);
            broadcast("[Auction] Sold to " + teamName + " for " + MoneyFormats.currency(amount)
                    + ". Waiting for payment to " + config.paymentUsername() + ".");
            return CloseLotResult.awaitingPayment(activeLot);
        }
    }

    public BidResult bid(UUID accountId, long amount) {
        synchronized (lock) {
            if (activeSession == null || !activeSession.isOpen()) {
                return BidResult.noSession();
            }
            if (activeLot == null) {
                return BidResult.noLot();
            }
            if (!activeLot.status().acceptsBids()) {
                if (activeLot.status() == AuctionLotStatus.COUNTDOWN) {
                    return BidResult.countdownLocked();
                }
                return BidResult.lotNotOpen();
            }
            if (amount <= 0) {
                return BidResult.invalidAmount();
            }
            Optional<AuctionRepresentative> rep;
            try {
                rep = repository.findRepresentativeByAccount(activeSession.sessionId(), accountId);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to resolve auction representative: " + exception.getMessage());
                return BidResult.failed(exception.getMessage());
            }
            if (rep.isEmpty()) {
                return BidResult.notRepresentative();
            }
            long minimum = activeLot.minimumNextBid();
            if (amount < minimum) {
                return BidResult.tooLow(minimum);
            }
            Optional<WalletAccount> wallet;
            try {
                wallet = money.accountByAccountId(accountId);
            } catch (Exception exception) {
                errorLogger.accept("Failed to load wallet for bid: " + exception.getMessage());
                return BidResult.failed(exception.getMessage());
            }
            if (wallet.isEmpty()) {
                return BidResult.walletMissing();
            }
            if (wallet.get().balance() < amount) {
                return BidResult.insufficientFunds(wallet.get().balance());
            }

            Instant now = clock.instant();
            AuctionBid bid = new AuctionBid(
                    UUID.randomUUID(),
                    activeLot.lotId(),
                    accountId,
                    rep.get().teamId(),
                    amount,
                    now);
            activeLot.applyBid(accountId, amount, now);
            try {
                repository.insertBid(bid);
                repository.upsertLot(activeLot);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to persist auction bid: " + exception.getMessage());
                return BidResult.failed(exception.getMessage());
            }
            String teamName = teams.teamOf(accountId).map(Team::displayName).orElse("Unknown");
            broadcast("[Auction] " + teamName + " bids " + MoneyFormats.currency(amount) + ".");
            return BidResult.accepted(bid, activeLot);
        }
    }

    /**
     * Match a successful {@code /pay} transfer to the pending lot payment.
     */
    public PaymentMatchResult onTransferSuccess(MoneyService.TransferSuccessEvent event) {
        synchronized (lock) {
            if (activeLot == null || !activeLot.status().isPaymentPending()) {
                return PaymentMatchResult.ignored();
            }
            if (activeLot.owedAccountId() == null || activeLot.owedAmount() == null) {
                return PaymentMatchResult.ignored();
            }
            if (event.amount() != activeLot.owedAmount()) {
                return PaymentMatchResult.ignored();
            }
            if (!event.sourceAccountId().equals(activeLot.owedAccountId())) {
                return PaymentMatchResult.ignored();
            }
            Optional<Account> amira;
            try {
                amira = accounts.findByUsernameIgnoreCase(config.paymentUsername());
            } catch (SQLException exception) {
                errorLogger.accept("Failed to resolve auction payment recipient: " + exception.getMessage());
                return PaymentMatchResult.failed(exception.getMessage());
            }
            if (amira.isEmpty() || !amira.get().id().equals(event.destinationAccountId())) {
                return PaymentMatchResult.ignored();
            }

            Instant now = clock.instant();
            boolean settled = money.ensureAuctionPaymentSettlement(
                    activeLot.lotId().toString(), event.transactionId(), event.amount());
            if (!settled) {
                return PaymentMatchResult.failed("Could not record auction payment settlement.");
            }
            activeLot.markPaid(event.transactionId(), now);
            try {
                repository.upsertLot(activeLot);
                // Keep the paid lot as the active pointer so /larp auction delivered works;
                // openLot is allowed because PAID does not blockNewLot.
            } catch (SQLException exception) {
                errorLogger.accept("Failed to mark lot paid: " + exception.getMessage());
                return PaymentMatchResult.failed(exception.getMessage());
            }
            String teamName = teamDisplayNameForAccount(activeLot.owedAccountId());
            broadcast("[Auction] Payment received from " + teamName + " for "
                    + MoneyFormats.currency(event.amount()) + ". Deliver the lot manually.");
            return PaymentMatchResult.matched(activeLot);
        }
    }

    public ForfeitResult forfeit() {
        synchronized (lock) {
            if (activeLot == null || !activeLot.status().isPaymentPending()) {
                return ForfeitResult.noPendingPayment();
            }
            UUID currentOwed = activeLot.owedAccountId();
            if (currentOwed == null || activeLot.owedAmount() == null) {
                return ForfeitResult.noPendingPayment();
            }

            List<AuctionBid> bids;
            try {
                bids = repository.listBidsChronological(activeLot.lotId());
            } catch (SQLException exception) {
                errorLogger.accept("Failed to load bids for forfeit: " + exception.getMessage());
                return ForfeitResult.failed(exception.getMessage());
            }

            Set<UUID> excluded = new HashSet<>(activeLot.excludedPayerIds());
            excluded.add(currentOwed);

            Instant now = clock.instant();
            for (int index = bids.size() - 1; index >= 0; index--) {
                AuctionBid candidate = bids.get(index);
                if (excluded.contains(candidate.accountId())) {
                    continue;
                }
                Optional<WalletAccount> wallet;
                try {
                    wallet = money.accountByAccountId(candidate.accountId());
                } catch (Exception exception) {
                    errorLogger.accept("Failed to load wallet during forfeit: " + exception.getMessage());
                    return ForfeitResult.failed(exception.getMessage());
                }
                if (wallet.isEmpty() || wallet.get().balance() < candidate.amount()) {
                    excluded.add(candidate.accountId());
                    continue;
                }
                activeLot.offerTo(candidate.accountId(), candidate.amount(), now);
                try {
                    repository.upsertLot(activeLot);
                } catch (SQLException exception) {
                    errorLogger.accept("Failed to offer lot to previous bidder: " + exception.getMessage());
                    return ForfeitResult.failed(exception.getMessage());
                }
                String teamName = teamDisplayNameForAccount(candidate.accountId());
                broadcast("[Auction] Previous bidder " + teamName + " may pay "
                        + MoneyFormats.currency(candidate.amount()) + " to " + config.paymentUsername() + ".");
                return ForfeitResult.offered(activeLot, candidate);
            }

            activeLot.markUnsold(now);
            try {
                repository.upsertLot(activeLot);
                repository.setRuntime(activeSession == null ? null : activeSession.sessionId(), null, now);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to mark lot unsold after forfeit: " + exception.getMessage());
                return ForfeitResult.failed(exception.getMessage());
            }
            AuctionLot unsold = activeLot;
            activeLot = null;
            broadcast("[Auction] Lot " + unsold.lotNumber() + " unsold — no previous bidder could pay.");
            return ForfeitResult.unsold(unsold);
        }
    }

    public AdminResult markDelivered() {
        synchronized (lock) {
            if (activeLot == null || activeLot.status() != AuctionLotStatus.PAID) {
                return AdminResult.fail("No paid lot awaiting delivery mark.");
            }
            Instant now = clock.instant();
            activeLot.markDelivered(now);
            try {
                repository.upsertLot(activeLot);
                repository.setRuntime(activeSession == null ? null : activeSession.sessionId(), null, now);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to mark lot delivered: " + exception.getMessage());
                return AdminResult.fail(exception.getMessage());
            }
            AuctionLot lot = activeLot;
            activeLot = null;
            broadcast("[Auction] Lot " + lot.lotNumber() + " marked delivered.");
            return AdminResult.ok("Lot " + lot.lotNumber() + " marked delivered.");
        }
    }

    public AdminResult endSession() {
        synchronized (lock) {
            if (activeSession == null || !activeSession.isOpen()) {
                return AdminResult.fail("No active auction session.");
            }
            if (activeLot != null && activeLot.status().blocksNewLot()) {
                return AdminResult.fail("Finish or resolve the active lot before ending the session.");
            }
            Instant now = clock.instant();
            activeSession.close(now);
            try {
                repository.upsertSession(activeSession);
                repository.setRuntime(null, null, now);
            } catch (SQLException exception) {
                errorLogger.accept("Failed to end auction session: " + exception.getMessage());
                return AdminResult.fail(exception.getMessage());
            }
            AuctionSession closed = activeSession;
            activeSession = null;
            activeLot = null;
            broadcast("[Auction] Session ended.");
            return AdminResult.ok("Auction session " + closed.sessionId() + " closed.");
        }
    }

    public StatusSnapshot status() {
        synchronized (lock) {
            List<AuctionRepresentative> reps = List.of();
            if (activeSession != null) {
                try {
                    reps = repository.listRepresentatives(activeSession.sessionId());
                } catch (SQLException exception) {
                    errorLogger.accept("Failed to load representatives for status: " + exception.getMessage());
                }
            }
            return new StatusSnapshot(activeSession, activeLot, reps, config.paymentUsername());
        }
    }

    public List<AuctionRepresentative> representatives() {
        synchronized (lock) {
            if (activeSession == null) {
                return List.of();
            }
            try {
                return repository.listRepresentatives(activeSession.sessionId());
            } catch (SQLException exception) {
                errorLogger.accept("Failed to list representatives: " + exception.getMessage());
                return List.of();
            }
        }
    }

    private boolean repsLocked() {
        return activeSession != null && activeSession.status() == AuctionSessionStatus.ACTIVE;
    }

    private void broadcast(String message) {
        for (AuctionEventListener listener : listeners) {
            listener.onBroadcast(message);
        }
    }

    private String resolveUsername(UUID accountId) {
        try {
            return accounts.findById(accountId).map(Account::username).orElse(accountId.toString());
        } catch (SQLException exception) {
            return accountId.toString();
        }
    }

    private String teamDisplayNameForAccount(UUID accountId) {
        return teams.teamOf(accountId).map(Team::displayName).orElse(resolveUsername(accountId));
    }

    public interface AuctionEventListener {
        void onBroadcast(String message);
    }

    public record StatusSnapshot(
            @Nullable AuctionSession session,
            @Nullable AuctionLot lot,
            List<AuctionRepresentative> representatives,
            String paymentUsername) {
    }

    public sealed interface SessionStartResult {
        record Started(AuctionSession session) implements SessionStartResult {
        }

        record AlreadyActive(AuctionSession session) implements SessionStartResult {
        }

        record Failed(String message) implements SessionStartResult {
        }

        static SessionStartResult started(AuctionSession session) {
            return new Started(session);
        }

        static SessionStartResult alreadyActive(AuctionSession session) {
            return new AlreadyActive(session);
        }

        static SessionStartResult failed(String message) {
            return new Failed(message);
        }
    }

    public sealed interface RepResult {
        record Ok(AuctionRepresentative representative, Team team) implements RepResult {
        }

        record NoSession() implements RepResult {
        }

        record Locked() implements RepResult {
        }

        record NotOnTeam() implements RepResult {
        }

        record AlreadyOtherTeam() implements RepResult {
        }

        record Failed(String message) implements RepResult {
        }

        static RepResult ok(AuctionRepresentative representative, Team team) {
            return new Ok(representative, team);
        }

        static RepResult noSession() {
            return new NoSession();
        }

        static RepResult locked() {
            return new Locked();
        }

        static RepResult notOnTeam() {
            return new NotOnTeam();
        }

        static RepResult alreadyOtherTeam() {
            return new AlreadyOtherTeam();
        }

        static RepResult failed(String message) {
            return new Failed(message);
        }
    }

    public sealed interface OpenLotResult {
        record Opened(AuctionLot lot) implements OpenLotResult {
        }

        record NoSession() implements OpenLotResult {
        }

        record LotBusy(AuctionLot lot) implements OpenLotResult {
        }

        record MissingReps(List<String> teamNames) implements OpenLotResult {
        }

        record InvalidTheme() implements OpenLotResult {
        }

        record InvalidStartingBid() implements OpenLotResult {
        }

        record Failed(String message) implements OpenLotResult {
        }

        static OpenLotResult opened(AuctionLot lot) {
            return new Opened(lot);
        }

        static OpenLotResult noSession() {
            return new NoSession();
        }

        static OpenLotResult lotBusy(AuctionLot lot) {
            return new LotBusy(lot);
        }

        static OpenLotResult missingReps(List<String> teamNames) {
            return new MissingReps(teamNames);
        }

        static OpenLotResult invalidTheme() {
            return new InvalidTheme();
        }

        static OpenLotResult invalidStartingBid() {
            return new InvalidStartingBid();
        }

        static OpenLotResult failed(String message) {
            return new Failed(message);
        }
    }

    public sealed interface CloseLotResult {
        record AwaitingPayment(AuctionLot lot) implements CloseLotResult {
        }

        record Unsold(AuctionLot lot) implements CloseLotResult {
        }

        record NoOpenLot() implements CloseLotResult {
        }

        record Failed(String message) implements CloseLotResult {
        }

        static CloseLotResult awaitingPayment(AuctionLot lot) {
            return new AwaitingPayment(lot);
        }

        static CloseLotResult unsold(AuctionLot lot) {
            return new Unsold(lot);
        }

        static CloseLotResult noOpenLot() {
            return new NoOpenLot();
        }

        static CloseLotResult failed(String message) {
            return new Failed(message);
        }
    }

    public sealed interface BidResult {
        record Accepted(AuctionBid bid, AuctionLot lot) implements BidResult {
        }

        record NoSession() implements BidResult {
        }

        record NoLot() implements BidResult {
        }

        record LotNotOpen() implements BidResult {
        }

        record CountdownLocked() implements BidResult {
        }

        record NotRepresentative() implements BidResult {
        }

        record InvalidAmount() implements BidResult {
        }

        record TooLow(long minimum) implements BidResult {
        }

        record InsufficientFunds(long balance) implements BidResult {
        }

        record WalletMissing() implements BidResult {
        }

        record Failed(String message) implements BidResult {
        }

        static BidResult accepted(AuctionBid bid, AuctionLot lot) {
            return new Accepted(bid, lot);
        }

        static BidResult noSession() {
            return new NoSession();
        }

        static BidResult noLot() {
            return new NoLot();
        }

        static BidResult lotNotOpen() {
            return new LotNotOpen();
        }

        static BidResult countdownLocked() {
            return new CountdownLocked();
        }

        static BidResult notRepresentative() {
            return new NotRepresentative();
        }

        static BidResult invalidAmount() {
            return new InvalidAmount();
        }

        static BidResult tooLow(long minimum) {
            return new TooLow(minimum);
        }

        static BidResult insufficientFunds(long balance) {
            return new InsufficientFunds(balance);
        }

        static BidResult walletMissing() {
            return new WalletMissing();
        }

        static BidResult failed(String message) {
            return new Failed(message);
        }
    }

    public sealed interface PaymentMatchResult {
        record Matched(AuctionLot lot) implements PaymentMatchResult {
        }

        record Ignored() implements PaymentMatchResult {
        }

        record Failed(String message) implements PaymentMatchResult {
        }

        static PaymentMatchResult matched(AuctionLot lot) {
            return new Matched(lot);
        }

        static PaymentMatchResult ignored() {
            return new Ignored();
        }

        static PaymentMatchResult failed(String message) {
            return new Failed(message);
        }
    }

    public sealed interface ForfeitResult {
        record Offered(AuctionLot lot, AuctionBid bid) implements ForfeitResult {
        }

        record Unsold(AuctionLot lot) implements ForfeitResult {
        }

        record NoPendingPayment() implements ForfeitResult {
        }

        record Failed(String message) implements ForfeitResult {
        }

        static ForfeitResult offered(AuctionLot lot, AuctionBid bid) {
            return new Offered(lot, bid);
        }

        static ForfeitResult unsold(AuctionLot lot) {
            return new Unsold(lot);
        }

        static ForfeitResult noPendingPayment() {
            return new NoPendingPayment();
        }

        static ForfeitResult failed(String message) {
            return new Failed(message);
        }
    }

    public sealed interface AdminResult {
        record Ok(String message) implements AdminResult {
        }

        record Fail(String message) implements AdminResult {
        }

        static AdminResult ok(String message) {
            return new Ok(message);
        }

        static AdminResult fail(String message) {
            return new Fail(message);
        }
    }
}
