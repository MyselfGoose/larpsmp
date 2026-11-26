package com.larpsmp.moneyevent.gladiator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure kit-ban turn logic for 1v1s. Bans alternate until one kit remains.
 */
public final class GladiatorKitBan {

    private GladiatorKitBan() {
    }

    public static List<String> allDuelKitIds() {
        List<String> kits = new ArrayList<>(GladiatorRules.DUEL_KIT_COUNT);
        for (int i = 1; i <= GladiatorRules.DUEL_KIT_COUNT; i++) {
            kits.add(GladiatorRules.duelKitId(i));
        }
        return List.copyOf(kits);
    }

    public static Set<String> remainingKits(List<String> bannedKitIds) {
        Objects.requireNonNull(bannedKitIds, "bannedKitIds");
        LinkedHashSet<String> remaining = new LinkedHashSet<>(allDuelKitIds());
        for (String banned : bannedKitIds) {
            remaining.remove(banned);
        }
        return Set.copyOf(remaining);
    }

    public static UUID nextBanner(UUID fighterA, UUID fighterB, int bansSoFar) {
        Objects.requireNonNull(fighterA, "fighterA");
        Objects.requireNonNull(fighterB, "fighterB");
        if (bansSoFar < 0) {
            throw new IllegalArgumentException("bansSoFar cannot be negative");
        }
        return bansSoFar % 2 == 0 ? fighterA : fighterB;
    }

    public static BanResult applyBan(
            UUID bannerAccountId,
            String kitId,
            UUID fighterA,
            UUID fighterB,
            List<String> alreadyBanned
    ) {
        Objects.requireNonNull(bannerAccountId, "bannerAccountId");
        Objects.requireNonNull(kitId, "kitId");
        Objects.requireNonNull(fighterA, "fighterA");
        Objects.requireNonNull(fighterB, "fighterB");
        Objects.requireNonNull(alreadyBanned, "alreadyBanned");

        if (!GladiatorRules.isDuelKitId(kitId)) {
            return BanResult.invalid("Unknown duel kit: " + kitId);
        }
        UUID expected = nextBanner(fighterA, fighterB, alreadyBanned.size());
        if (!expected.equals(bannerAccountId)) {
            return BanResult.invalid("It is not your turn to ban.");
        }
        Set<String> remaining = remainingKits(alreadyBanned);
        if (!remaining.contains(kitId)) {
            return BanResult.invalid("Kit " + kitId + " is already banned.");
        }
        if (remaining.size() <= 1) {
            return BanResult.invalid("Only one kit remains; ban phase is over.");
        }
        List<String> nextBanned = new ArrayList<>(alreadyBanned);
        nextBanned.add(kitId);
        Set<String> after = remainingKits(nextBanned);
        if (after.size() == 1) {
            String remainingKit = after.iterator().next();
            return BanResult.complete(List.copyOf(nextBanned), remainingKit);
        }
        UUID next = nextBanner(fighterA, fighterB, nextBanned.size());
        return BanResult.continueBan(List.copyOf(nextBanned), next);
    }

    public sealed interface BanResult {
        boolean success();

        String message();

        record Invalid(String message) implements BanResult {
            @Override
            public boolean success() {
                return false;
            }
        }

        record Continue(List<String> bannedKitIds, UUID nextBannerAccountId) implements BanResult {
            @Override
            public boolean success() {
                return true;
            }

            @Override
            public String message() {
                return "Kit banned. Next ban: " + nextBannerAccountId;
            }
        }

        record Complete(List<String> bannedKitIds, String remainingKitId) implements BanResult {
            @Override
            public boolean success() {
                return true;
            }

            @Override
            public String message() {
                return "Kit ban complete. Remaining kit: " + remainingKitId;
            }
        }

        static BanResult invalid(String message) {
            return new Invalid(message);
        }

        static BanResult continueBan(List<String> banned, UUID next) {
            return new Continue(banned, next);
        }

        static BanResult complete(List<String> banned, String remaining) {
            return new Complete(banned, remaining);
        }
    }
}
