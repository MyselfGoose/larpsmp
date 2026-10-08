package com.larpsmp.moneyevent.command;

import com.larpsmp.moneyevent.wallet.MoneyService;
import com.larpsmp.moneyevent.wallet.WalletAccount;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WalletPlayerLookup implements PlayerLookup {
    private final MoneyService moneyService;
    private final OnlinePlayerAccess onlinePlayers;

    public WalletPlayerLookup(MoneyService moneyService, OnlinePlayerAccess onlinePlayers) {
        this.moneyService = moneyService;
        this.onlinePlayers = onlinePlayers;
    }

    @Override
    public PlayerLookupResult resolve(String username) {
        List<OnlinePlayerIdentity> onlineMatches = onlinePlayers.onlinePlayers().stream()
                .filter(player -> player.username().equalsIgnoreCase(username))
                .toList();
        if (onlineMatches.size() > 1) {
            return PlayerLookupResult.status(PlayerLookupResult.Status.AMBIGUOUS);
        }

        try {
            List<WalletAccount> matches = moneyService.registeredAccounts().stream()
                    .filter(account -> account.lastKnownUsername().equalsIgnoreCase(username))
                    .toList();
            if (matches.size() > 1) {
                return PlayerLookupResult.status(PlayerLookupResult.Status.AMBIGUOUS);
            }
            if (onlineMatches.size() == 1) {
                return PlayerLookupResult.found(onlineMatches.getFirst());
            }
            if (matches.isEmpty()) {
                return PlayerLookupResult.status(PlayerLookupResult.Status.NOT_FOUND);
            }
            WalletAccount account = matches.getFirst();
            return PlayerLookupResult.found(
                    new OnlinePlayerIdentity(account.playerId(), account.lastKnownUsername()));
        } catch (IOException exception) {
            return PlayerLookupResult.status(PlayerLookupResult.Status.STORAGE_FAILURE);
        }
    }

    @Override
    public List<String> suggestions() {
        Map<String, String> names = new LinkedHashMap<>();
        for (OnlinePlayerIdentity player : onlinePlayers.onlinePlayers()) {
            names.putIfAbsent(player.username().toLowerCase(Locale.ROOT), player.username());
        }
        try {
            for (WalletAccount account : moneyService.registeredAccounts()) {
                names.putIfAbsent(account.lastKnownUsername().toLowerCase(Locale.ROOT), account.lastKnownUsername());
            }
        } catch (IOException ignored) {
            // Online suggestions remain useful; command execution reports storage failures explicitly.
        }
        return new ArrayList<>(names.values());
    }
}
