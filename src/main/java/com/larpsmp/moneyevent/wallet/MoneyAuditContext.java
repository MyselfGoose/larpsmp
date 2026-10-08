package com.larpsmp.moneyevent.wallet;

import java.util.Objects;

public record MoneyAuditContext(
        String authorizationAction,
        String command,
        String actorUsername,
        String senderType) {
    public static final MoneyAuditContext EMPTY = new MoneyAuditContext("", "", "", "");

    public MoneyAuditContext {
        authorizationAction = Objects.requireNonNullElse(authorizationAction, "");
        command = Objects.requireNonNullElse(command, "");
        actorUsername = Objects.requireNonNullElse(actorUsername, "");
        senderType = Objects.requireNonNullElse(senderType, "");
    }
}
