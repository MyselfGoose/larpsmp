package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.team.TeamAdminAction;
import com.larpsmp.moneyevent.team.TeamAdminAuthorizer;

/** Temporary local-development fallback. Replace this adapter with Azeem's authorization adapter. */
public final class DevelopmentConsoleOpAuthorizer implements MoneyAdminAuthorizer, TeamAdminAuthorizer {
    @Override
    public boolean isAllowed(CommandSource sender, MoneyAdminAction action) {
        return sender.isConsole() || sender.isOperator();
    }

    @Override
    public boolean isAllowed(CommandSource sender, TeamAdminAction action) {
        return sender.isConsole() || sender.isOperator();
    }
}
