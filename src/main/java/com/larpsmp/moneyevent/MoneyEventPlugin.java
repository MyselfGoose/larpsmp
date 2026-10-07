package com.larpsmp.moneyevent;

import com.larpsmp.moneyevent.auth.AuthConfig;
import com.larpsmp.moneyevent.auth.AuthConnectionListener;
import com.larpsmp.moneyevent.auth.AuthCredentialsValidator;
import com.larpsmp.moneyevent.auth.AuthDialogFactory;
import com.larpsmp.moneyevent.auth.AuthSessionManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class MoneyEventPlugin extends JavaPlugin {

    private AuthSessionManager authSessionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        AuthConfig authConfig = AuthConfig.from(getConfig());

        authSessionManager = new AuthSessionManager();
        AuthCredentialsValidator validator = new AuthCredentialsValidator(authConfig.testAccount());
        AuthDialogFactory dialogFactory = new AuthDialogFactory(authConfig.messages());
        AuthConnectionListener authListener = new AuthConnectionListener(
                authConfig,
                validator,
                authSessionManager,
                dialogFactory,
                getLogger()
        );

        getServer().getPluginManager().registerEvents(authListener, this);

        if (authConfig.enabled()) {
            getLogger().info("Pre-join authentication enabled (test user: "
                    + authConfig.testAccount().username() + ").");
        } else {
            getLogger().info("Pre-join authentication disabled.");
        }
        getLogger().info("Money Event plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (authSessionManager != null) {
            authSessionManager.cancelAll();
            authSessionManager = null;
        }
        getLogger().info("Money Event plugin disabled.");
    }
}
