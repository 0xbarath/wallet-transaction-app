package com.wallet.txhistory.exception;

public class AlchemyApiException extends RuntimeException {
    public AlchemyApiException(String message) {
        super(message);
    }

    public AlchemyApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
