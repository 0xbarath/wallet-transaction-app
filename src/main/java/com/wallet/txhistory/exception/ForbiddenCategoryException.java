package com.wallet.txhistory.exception;

public class ForbiddenCategoryException extends RuntimeException {
    public ForbiddenCategoryException(String message) {
        super(message);
    }
}
