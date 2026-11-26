package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.team.TeamAdminAction;
import com.larpsmp.moneyevent.team.TeamAdminAuthorizer;

/**
 * Production authorizer: console, plugin admin, or server operator may run plugin admin commands.
 * Server-operator management is console or existing server operators only.
 */
public final class AccountPermissionAuthorizer implements MoneyAdminAuthorizer, TeamAdminAuthorizer {

    @Override
    public boolean isAllowed(CommandSource sender, MoneyAdminAction action) {
        return hasPluginPrivilege(sender);
    }

    @Override
    public boolean isAllowed(CommandSource sender, TeamAdminAction action) {
        return hasPluginPrivilege(sender);
    }

    public boolean hasPluginPrivilege(CommandSource sender) {
        return sender.isConsole() || sender.isPluginAdmin() || sender.isServerOperator();
    }

    public boolean canManageServerOperators(CommandSource sender) {
        return sender.isConsole() || sender.isServerOperator();
    }
}
