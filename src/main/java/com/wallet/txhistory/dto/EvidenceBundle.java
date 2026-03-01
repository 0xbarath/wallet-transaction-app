package com.wallet.txhistory.dto;

import com.wallet.txhistory.model.Transfer;

import java.util.List;

public record EvidenceBundle(
        TransactionReceipt receipt,
        List<ImmutableEvidenceItem> evidenceItems,
        List<Transfer> localTransfers
) {
}
