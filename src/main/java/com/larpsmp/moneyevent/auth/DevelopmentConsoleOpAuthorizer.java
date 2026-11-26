package com.larpsmp.moneyevent.auth;

import com.larpsmp.moneyevent.command.CommandSource;
import com.larpsmp.moneyevent.team.TeamAdminAction;
import com.larpsmp.moneyevent.team.TeamAdminAuthorizer;

/**
 * @deprecated Replaced by {@link AccountPermissionAuthorizer}. Kept as a thin delegate for older tests.
 */
@Deprecated
public final class DevelopmentConsoleOpAuthorizer implements MoneyAdminAuthorizer, TeamAdminAuthorizer {

    private final AccountPermissionAuthorizer delegate = new AccountPermissionAuthorizer();

    @Override
    public boolean isAllowed(CommandSource sender, MoneyAdminAction action) {
        return delegate.isAllowed(sender, action);
    }

    @Override
    public boolean isAllowed(CommandSource sender, TeamAdminAction action) {
        return delegate.isAllowed(sender, action);
    }
}
