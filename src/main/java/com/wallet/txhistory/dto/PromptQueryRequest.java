package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.UUID;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutablePromptQueryRequest.class)
public interface PromptQueryRequest {
    @NotNull UUID walletId();
    @NotBlank @Size(max = 2000) String prompt();
    @Nullable Integer limit();
    @Nullable String cursor();
}
