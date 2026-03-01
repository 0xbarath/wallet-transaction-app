package com.wallet.txhistory.exception;

import java.util.List;

public class PromptParseException extends RuntimeException {
    private final List<String> needsClarification;

    public PromptParseException(String message, List<String> needsClarification) {
        super(message);
        this.needsClarification = needsClarification != null ? needsClarification : List.of();
    }

    public PromptParseException(String message) {
        this(message, List.of());
    }

    public List<String> getNeedsClarification() {
        return needsClarification;
    }
}
