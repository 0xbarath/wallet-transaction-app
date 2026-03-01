package com.wallet.txhistory.exception;

import java.util.UUID;

public class SyncInProgressException extends RuntimeException {
    public SyncInProgressException(UUID walletId) {
        super("Sync already in progress for wallet: " + walletId);
    }
}
