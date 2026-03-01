package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import java.util.List;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableExplanation.class)
public interface Explanation {
    String summary();
    List<ImmutableExplanationStep> steps();
    List<String> unknowns();
    List<String> safetyNotes();
}
