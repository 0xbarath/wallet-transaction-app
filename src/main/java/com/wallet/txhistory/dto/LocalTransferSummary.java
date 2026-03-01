package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableLocalTransferSummary.class)
public interface LocalTransferSummary {
    String walletId();
    String direction();
    @Nullable String asset();
    @Nullable String value();
    String category();
    long blockNum();
}
