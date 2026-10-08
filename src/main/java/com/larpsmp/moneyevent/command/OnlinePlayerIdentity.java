package com.larpsmp.moneyevent.command;

import java.util.UUID;

public record OnlinePlayerIdentity(UUID playerId, String username) {
}
