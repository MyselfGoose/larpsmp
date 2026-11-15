package com.larpsmp.moneyevent.combat;

import com.larpsmp.moneyevent.auth.Account;
import com.larpsmp.moneyevent.auth.AccountIdentity;
import com.larpsmp.moneyevent.auth.AccountRepository;
import com.larpsmp.moneyevent.playerstate.AccountSession;
import com.larpsmp.moneyevent.playerstate.AccountSessionManager;
import com.larpsmp.moneyevent.team.PlayerTier;
import com.larpsmp.moneyevent.team.Team;
import com.larpsmp.moneyevent.team.TeamService;
import com.larpsmp.moneyevent.wallet.DeathPaymentRequest;
import com.larpsmp.moneyevent.wallet.DeathPaymentResult;
import com.larpsmp.moneyevent.wallet.MoneyService;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Confirms PvP deaths from attack memory and settles money through {@link MoneyService}.
 * Does not punish combat logging or disconnects.
 */
public final class KillAttributionService {

    private final AttackMemory attackMemory;
    private final AccountSessionManager sessions;
    private final AccountRepository accounts;
    private final TeamService teams;
    private final MoneyService money;
    private final JdbcCombatKillRepository killRepository;
    private final EventCombatScope eventCombatScope;
    private final Clock clock;
    private final Consumer<String> errorLogger;

    public KillAttributionService(
            AttackMemory attackMemory,
            AccountSessionManager sessions,
            AccountRepository accounts,
            TeamService teams,
            MoneyService money,
            JdbcCombatKillRepository killRepository,
            EventCombatScope eventCombatScope,
            Consumer<String> errorLogger
    ) {
        this(
                attackMemory,
                sessions,
                accounts,
                teams,
                money,
                killRepository,
                eventCombatScope,
                Clock.systemUTC(),
                errorLogger
        );
    }

    public KillAttributionService(
            AttackMemory attackMemory,
            AccountSessionManager sessions,
            AccountRepository accounts,
            TeamService teams,
            MoneyService money,
            JdbcCombatKillRepository killRepository,
            EventCombatScope eventCombatScope,
            Clock clock,
            Consumer<String> errorLogger
    ) {
        this.attackMemory = Objects.requireNonNull(attackMemory, "attackMemory");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.teams = Objects.requireNonNull(teams, "teams");
        this.money = Objects.requireNonNull(money, "money");
        this.killRepository = Objects.requireNonNull(killRepository, "killRepository");
        this.eventCombatScope = Objects.requireNonNull(eventCombatScope, "eventCombatScope");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.errorLogger = Objects.requireNonNull(errorLogger, "errorLogger");
    }

    public AttackMemory attackMemory() {
        return attackMemory;
    }

    /**
     * Attributes a player death using the last attacker within the 60-second window.
     *
     * @param victimMinecraftUuid dying player's Minecraft profile UUID
     * @param deathDamageCauseHint optional final damage hint (e.g. FALL → FALL_AFTER_HIT in evidence)
     */
    public AttributionOutcome attributeDeath(
            UUID victimMinecraftUuid,
            @Nullable AttributionCause deathDamageCauseHint
    ) {
        Objects.requireNonNull(victimMinecraftUuid, "victimMinecraftUuid");
        Instant now = clock.instant();

        Optional<AttackRecord> attackOpt = attackMemory.lastAttacker(victimMinecraftUuid, now);
        if (attackOpt.isEmpty()) {
            return AttributionOutcome.environmental("No qualifying last attacker within "
                    + CombatRules.ATTRIBUTION_WINDOW.getSeconds() + "s");
        }
        AttackRecord attack = attackOpt.get();

        Optional<ResolvedAccount> victimOpt = resolveAccount(victimMinecraftUuid);
        if (victimOpt.isEmpty()) {
            return AttributionOutcome.skipped("Victim has no LarpSMP account binding");
        }
        Optional<ResolvedAccount> killerOpt = resolveAccount(attack.attackerMinecraftUuid());
        if (killerOpt.isEmpty()) {
            return AttributionOutcome.skipped("Killer has no LarpSMP account binding");
        }

        ResolvedAccount victim = victimOpt.get();
        ResolvedAccount killer = killerOpt.get();
        if (victim.accountId().equals(killer.accountId())) {
            attackMemory.clear(victimMinecraftUuid);
            return AttributionOutcome.environmental("Self-attributed death ignored");
        }

        Optional<PlayerTier> tierOpt = teams.tierOf(victim.accountId());
        if (tierOpt.isEmpty()) {
            return AttributionOutcome.skipped("Victim has no competitor tier");
        }

        AttributionCause causeForEvidence = refineCause(attack.cause(), deathDamageCauseHint);
        boolean friendlyFire = isFriendlyFire(victim.accountId(), killer.accountId());
        String evidence = buildEvidence(causeForEvidence, killer.username(), attack, now);
        String deathId = "death-" + UUID.randomUUID();

        DeathPaymentResult settlement = money.processDeathPayment(new DeathPaymentRequest(
                deathId,
                victim.accountId(),
                killer.accountId(),
                tierOpt.get(),
                friendlyFire,
                evidence
        ));

        boolean eventKill = eventCombatScope.isEventCombat(victim.accountId(), killer.accountId());
        CombatKillRecord killRecord = new CombatKillRecord(
                deathId,
                victim.accountId(),
                killer.accountId(),
                causeForEvidence,
                evidence,
                eventKill,
                friendlyFire,
                settlement.outcome(),
                now
        );
        try {
            killRepository.insert(killRecord);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to persist combat kill " + deathId + ": " + exception.getMessage());
        }

        attackMemory.clear(victimMinecraftUuid);
        return AttributionOutcome.attributed(
                deathId, settlement, killRecord, tierOpt.get(), victim.username(), killer.username());
    }

    /**
     * Test/helper entry: attribute using a pre-built death id (idempotent settlement).
     */
    public AttributionOutcome attributeDeathWithId(
            String deathId,
            UUID victimMinecraftUuid,
            UUID killerMinecraftUuid,
            AttributionCause cause,
            Instant attackAt,
            @Nullable String detail
    ) {
        Objects.requireNonNull(deathId, "deathId");
        Objects.requireNonNull(victimMinecraftUuid, "victimMinecraftUuid");
        Objects.requireNonNull(killerMinecraftUuid, "killerMinecraftUuid");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(attackAt, "attackAt");

        Instant now = clock.instant();
        AttackRecord attack = new AttackRecord(killerMinecraftUuid, cause, attackAt, detail);
        if (attack.at().plus(CombatRules.ATTRIBUTION_WINDOW).isBefore(now)) {
            return AttributionOutcome.environmental("Attack outside attribution window");
        }

        try {
            Optional<CombatKillRecord> existing = killRepository.findByDeathId(deathId);
            if (existing.isPresent()) {
                return AttributionOutcome.duplicate(deathId, existing.get());
            }
        } catch (SQLException exception) {
            errorLogger.accept("Failed to check combat kill " + deathId + ": " + exception.getMessage());
            return AttributionOutcome.skipped("Combat kill lookup failed");
        }

        Optional<ResolvedAccount> victimOpt = resolveAccount(victimMinecraftUuid);
        Optional<ResolvedAccount> killerOpt = resolveAccount(killerMinecraftUuid);
        if (victimOpt.isEmpty() || killerOpt.isEmpty()) {
            return AttributionOutcome.skipped("Missing account binding for victim or killer");
        }
        ResolvedAccount victim = victimOpt.get();
        ResolvedAccount killer = killerOpt.get();
        Optional<PlayerTier> tierOpt = teams.tierOf(victim.accountId());
        if (tierOpt.isEmpty()) {
            return AttributionOutcome.skipped("Victim has no competitor tier");
        }

        boolean friendlyFire = isFriendlyFire(victim.accountId(), killer.accountId());
        String evidence = buildEvidence(cause, killer.username(), attack, now);
        DeathPaymentResult settlement = money.processDeathPayment(new DeathPaymentRequest(
                deathId,
                victim.accountId(),
                killer.accountId(),
                tierOpt.get(),
                friendlyFire,
                evidence
        ));

        boolean eventKill = eventCombatScope.isEventCombat(victim.accountId(), killer.accountId());
        CombatKillRecord killRecord = new CombatKillRecord(
                deathId,
                victim.accountId(),
                killer.accountId(),
                cause,
                evidence,
                eventKill,
                friendlyFire,
                settlement.outcome(),
                now
        );
        try {
            killRepository.insert(killRecord);
        } catch (SQLException exception) {
            errorLogger.accept("Failed to persist combat kill " + deathId + ": " + exception.getMessage());
        }
        return AttributionOutcome.attributed(
                deathId, settlement, killRecord, tierOpt.get(), victim.username(), killer.username());
    }

    private boolean isFriendlyFire(UUID victimAccountId, UUID killerAccountId) {
        Optional<Team> victimTeam = teams.teamOf(victimAccountId);
        Optional<Team> killerTeam = teams.teamOf(killerAccountId);
        return victimTeam.isPresent()
                && killerTeam.isPresent()
                && victimTeam.get().id().equals(killerTeam.get().id());
    }

    private Optional<ResolvedAccount> resolveAccount(UUID minecraftUuid) {
        Optional<AccountSession> session = sessions.findByMinecraftUuid(minecraftUuid);
        if (session.isPresent()) {
            return Optional.of(new ResolvedAccount(session.get().accountId(), session.get().username()));
        }
        try {
            Optional<AccountIdentity> identity = accounts.findIdentityByMinecraftUuid(minecraftUuid);
            if (identity.isEmpty()) {
                return Optional.empty();
            }
            Optional<Account> account = accounts.findById(identity.get().accountId());
            if (account.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ResolvedAccount(account.get().id(), account.get().username()));
        } catch (SQLException exception) {
            errorLogger.accept("Failed to resolve account for Minecraft UUID " + minecraftUuid
                    + ": " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static AttributionCause refineCause(
            AttributionCause attackCause,
            @Nullable AttributionCause deathHint
    ) {
        if (deathHint == AttributionCause.FALL_AFTER_HIT
                && attackCause != AttributionCause.FALL_AFTER_HIT) {
            return AttributionCause.FALL_AFTER_HIT;
        }
        return attackCause;
    }

    private static String buildEvidence(
            AttributionCause cause,
            String killerUsername,
            AttackRecord attack,
            Instant now
    ) {
        long secondsAgo = Math.max(0L, Duration.between(attack.at(), now).getSeconds());
        String base = cause.evidenceLabel() + " by " + killerUsername + " " + secondsAgo + "s ago";
        if (attack.detail() != null && !attack.detail().isBlank()) {
            return base + " (" + attack.detail() + ")";
        }
        return base;
    }

    private record ResolvedAccount(UUID accountId, String username) {
    }
}
