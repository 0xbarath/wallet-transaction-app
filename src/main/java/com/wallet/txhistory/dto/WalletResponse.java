package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableWalletResponse.class)
public interface WalletResponse {
    UUID id();
    String address();
    String network();
    @Nullable String label();
    OffsetDateTime createdAt();
    OffsetDateTime updatedAt();
}
