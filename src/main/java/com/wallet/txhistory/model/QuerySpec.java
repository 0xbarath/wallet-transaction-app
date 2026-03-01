package com.wallet.txhistory.model;

import com.wallet.txhistory.dto.WalletStyle;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Value.Immutable
@WalletStyle
public interface QuerySpec {
    int DEFAULT_LIMIT = 50;
    int MAX_LIMIT = 200;
    String SORT_CREATED_AT_DESC = "createdAt_desc";
    String SORT_CREATED_AT_ASC = "createdAt_asc";

    UUID walletId();
    @Nullable Direction direction();
    @Value.Default default List<TransferCategory> categories() { return List.of(); }
    @Value.Default default List<String> assets() { return List.of(); }
    @Nullable BigDecimal minValue();
    @Nullable BigDecimal maxValue();
    @Nullable String counterparty();
    @Nullable OffsetDateTime startTime();
    @Nullable OffsetDateTime endTime();
    @Value.Default default String sort() { return SORT_CREATED_AT_DESC; }
    @Nullable String cursor();
    @Value.Default default int limit() { return DEFAULT_LIMIT; }

    @Value.Check
    default QuerySpec normalize() {
        int normalizedLimit = limit();
        if (normalizedLimit <= 0) normalizedLimit = DEFAULT_LIMIT;
        if (normalizedLimit > MAX_LIMIT) normalizedLimit = MAX_LIMIT;
        if (normalizedLimit != limit()) {
            return ImmutableQuerySpec.copyOf(this).withLimit(normalizedLimit);
        }
        return this;
    }
}
