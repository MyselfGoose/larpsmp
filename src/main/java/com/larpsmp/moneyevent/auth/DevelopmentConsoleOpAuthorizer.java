package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.command.CommandSource;

/** Temporary local-development fallback. Replace this adapter with Azeem's authorization adapter. */
public final class DevelopmentConsoleOpAuthorizer implements MoneyAdminAuthorizer {
    @Override
    public boolean isAllowed(CommandSource sender, MoneyAdminAction action) {
        return sender.isConsole() || sender.isOperator();
    }
}
