package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableOperationResult.class)
public interface OperationResult {
    String name();
    double confidence();
    List<String> evidenceIds();
}
