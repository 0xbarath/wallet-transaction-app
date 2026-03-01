package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableExplanationStep.class)
public interface ExplanationStep {
    String text();
    List<String> evidenceIds();
}
