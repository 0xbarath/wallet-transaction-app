package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import java.util.Map;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableEvidenceItem.class)
public interface EvidenceItem {
    String id();
    String type();
    Map<String, Object> fields();
}
