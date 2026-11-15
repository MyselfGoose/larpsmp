package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.team.TeamAdminAction;
import com.larpsmp.moneyevent.team.TeamAdminAuthorizer;

/**
 * Allows the server console and LarpSMP account admins.
 *
 * <p>Player {@link CommandSource#isOperator()} must be wired to
 * {@link AccountAdminService#isAdminMinecraftUuid(java.util.UUID)} (not Paper's client-UUID ops list).
 */
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
