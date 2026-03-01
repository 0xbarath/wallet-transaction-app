package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Size;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableUpdateWalletRequest.class)
public interface UpdateWalletRequest {
    @Nullable @Size(max = 64) String label();
}
