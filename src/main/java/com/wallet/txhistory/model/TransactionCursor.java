package com.wallet.txhistory.model;

import com.wallet.txhistory.exception.InvalidCursorException;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

public record TransactionCursor(OffsetDateTime createdAt, UUID id) {

    private static final String SEPARATOR = "|";

    public String encode() {
        String raw = createdAt.toString() + SEPARATOR + id.toString();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
    }

    public static TransactionCursor decode(String encoded) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded));
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                throw new InvalidCursorException("Invalid cursor format");
            }
            return new TransactionCursor(
                    OffsetDateTime.parse(parts[0]),
                    UUID.fromString(parts[1])
            );
        } catch (InvalidCursorException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidCursorException("Invalid cursor: " + e.getMessage());
        }
    }
}
