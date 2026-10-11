package com.larpsmp.moneyevent.team;

import com.larpsmp.moneyevent.command.CommandSource;

@FunctionalInterface
public interface TeamAdminAuthorizer {
    boolean isAllowed(CommandSource sender, TeamAdminAction action);
}
