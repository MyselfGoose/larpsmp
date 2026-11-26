package com.larpsmp.moneyevent.gladiator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GladiatorKitBanTest {

    @Test
    void bansAlternateUntilOneKitRemains() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertEquals(a, GladiatorKitBan.nextBanner(a, b, 0));
        assertEquals(b, GladiatorKitBan.nextBanner(a, b, 1));

        GladiatorKitBan.BanResult first = GladiatorKitBan.applyBan(a, "1", a, b, List.of());
        assertInstanceOf(GladiatorKitBan.BanResult.Continue.class, first);
        GladiatorKitBan.BanResult.Continue c1 = (GladiatorKitBan.BanResult.Continue) first;
        assertEquals(b, c1.nextBannerAccountId());

        GladiatorKitBan.BanResult second = GladiatorKitBan.applyBan(b, "2", a, b, c1.bannedKitIds());
        assertInstanceOf(GladiatorKitBan.BanResult.Continue.class, second);
        GladiatorKitBan.BanResult.Continue c2 = (GladiatorKitBan.BanResult.Continue) second;

        GladiatorKitBan.BanResult third = GladiatorKitBan.applyBan(a, "3", a, b, c2.bannedKitIds());
        assertInstanceOf(GladiatorKitBan.BanResult.Continue.class, third);
        GladiatorKitBan.BanResult.Continue c3 = (GladiatorKitBan.BanResult.Continue) third;

        GladiatorKitBan.BanResult fourth = GladiatorKitBan.applyBan(b, "4", a, b, c3.bannedKitIds());
        assertInstanceOf(GladiatorKitBan.BanResult.Complete.class, fourth);
        GladiatorKitBan.BanResult.Complete done = (GladiatorKitBan.BanResult.Complete) fourth;
        assertEquals("5", done.remainingKitId());
        assertEquals(4, done.bannedKitIds().size());
    }

    @Test
    void wrongTurnAndDuplicateBanRejected() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertFalse(GladiatorKitBan.applyBan(b, "1", a, b, List.of()).success());
        assertFalse(GladiatorKitBan.applyBan(a, "9", a, b, List.of()).success());
        GladiatorKitBan.BanResult first = GladiatorKitBan.applyBan(a, "1", a, b, List.of());
        assertTrue(first.success());
        List<String> banned = ((GladiatorKitBan.BanResult.Continue) first).bannedKitIds();
        assertFalse(GladiatorKitBan.applyBan(b, "1", a, b, banned).success());
    }
}
