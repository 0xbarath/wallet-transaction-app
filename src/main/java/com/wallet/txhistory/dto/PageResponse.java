package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.wallet.txhistory.model.QuerySpec;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutablePageResponse.class)
public interface PageResponse<T> {
    List<T> items();
    @Nullable String nextCursor();
    QuerySpec querySpec();
}
