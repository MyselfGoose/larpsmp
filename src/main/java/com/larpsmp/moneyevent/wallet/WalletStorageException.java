package com.larpsmp.moneyevent.wallet;

import java.io.IOException;

public final class WalletStorageException extends IOException {
    public WalletStorageException(String message) {
        super(message);
    }

    public WalletStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
