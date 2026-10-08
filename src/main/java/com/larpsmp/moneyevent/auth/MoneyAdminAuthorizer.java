package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.command.CommandSource;

@FunctionalInterface
public interface MoneyAdminAuthorizer {
    boolean isAllowed(CommandSource sender, MoneyAdminAction action);
}
