package com.wallet.txhistory.exception;

public class DuplicateWalletException extends RuntimeException {
    public DuplicateWalletException(String address, String network) {
        super("Wallet already registered: " + address + " on " + network);
    }
}
