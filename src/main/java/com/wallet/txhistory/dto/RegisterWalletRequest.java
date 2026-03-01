package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableRegisterWalletRequest.class)
public interface RegisterWalletRequest {
    @NotBlank
    @Pattern(regexp = "^0x[a-fA-F0-9]{40}$", message = "Invalid EVM address")
    String address();

    @Pattern(regexp = "^[a-z0-9-]+$", message = "Invalid network identifier")
    @Value.Default default String network() { return "eth-mainnet"; }

    @Nullable String label();
}
