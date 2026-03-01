package com.wallet.txhistory.unit;

import com.wallet.txhistory.exception.InvalidCursorException;
import com.wallet.txhistory.model.TransactionCursor;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionCursorTest {

    @Test
    void encodeDecodeRoundtrip() {
        OffsetDateTime ts = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);
        UUID id = UUID.randomUUID();
        TransactionCursor cursor = new TransactionCursor(ts, id);

        String encoded = cursor.encode();
        TransactionCursor decoded = TransactionCursor.decode(encoded);

        assertThat(decoded.createdAt()).isEqualTo(ts);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    void encodeProducesBase64() {
        OffsetDateTime ts = OffsetDateTime.now(ZoneOffset.UTC);
        UUID id = UUID.randomUUID();
        TransactionCursor cursor = new TransactionCursor(ts, id);

        String encoded = cursor.encode();
        assertThat(encoded).isNotBlank();
        assertThat(encoded).doesNotContain("|");
    }

    @Test
    void decodeInvalidCursorThrows() {
        assertThatThrownBy(() -> TransactionCursor.decode("not-valid-base64!!!"))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void decodeEmptyPayloadThrows() {
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("invalid".getBytes());
        assertThatThrownBy(() -> TransactionCursor.decode(encoded))
                .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    void decodeMalformedTimestampThrows() {
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("not-a-date|" + UUID.randomUUID()).getBytes());
        assertThatThrownBy(() -> TransactionCursor.decode(encoded))
                .isInstanceOf(InvalidCursorException.class);
    }
}
