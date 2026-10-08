package com.larpsmp.moneyevent.command;

import java.util.List;

public interface PlayerLookup {
    PlayerLookupResult resolve(String username);

    List<String> suggestions();
}
