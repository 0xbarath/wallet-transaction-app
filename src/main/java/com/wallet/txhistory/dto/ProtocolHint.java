package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableProtocolHint.class)
public interface ProtocolHint {
    String address();
    String protocol();
    String label();
    double confidence();
    String source();
    @Nullable String category();
}
