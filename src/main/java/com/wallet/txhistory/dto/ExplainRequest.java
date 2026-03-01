package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.immutables.value.Value;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableExplainRequest.class)
public interface ExplainRequest {

    @NotBlank
    @Pattern(regexp = "^0x[a-fA-F0-9]{64}$", message = "Invalid transaction hash")
    String txHash();

    @Value.Default
    default String network() { return "eth-mainnet"; }

    @Value.Default
    default boolean explain() { return true; }

    @Value.Default
    default String format() { return "json"; }
}
