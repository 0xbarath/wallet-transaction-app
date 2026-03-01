package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableTransferResponse.class)
public interface TransferResponse {
    UUID id();
    UUID walletId();
    String network();
    @Nullable String hash();
    @Nullable Long blockNum();
    @Nullable OffsetDateTime blockTs();
    @Nullable String fromAddr();
    @Nullable String toAddr();
    @Nullable String direction();
    @Nullable String asset();
    @Nullable String category();
    @Nullable BigDecimal valueDecimal();
    @Nullable String rawValue();
    @Nullable String rawContractAddr();
    @Nullable Integer rawContractDecimals();
    @Nullable String tokenId();
    @Nullable OffsetDateTime createdAt();
}
