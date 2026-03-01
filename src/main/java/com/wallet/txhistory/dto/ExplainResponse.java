package com.wallet.txhistory.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

@Value.Immutable
@WalletStyle
@JsonDeserialize(as = ImmutableExplainResponse.class)
public interface ExplainResponse {
    String txHash();
    String network();
    String status();
    List<ImmutableProtocolHint> protocolHints();
    ImmutableOperationResult operation();
    @Nullable ImmutableExplanation explanation();
    List<ImmutableEvidenceItem> evidence();
    @Nullable List<ImmutableLocalTransferSummary> localTransfers();
    @Nullable String humanReadable();
}
