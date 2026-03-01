package com.wallet.txhistory.dto;

import java.util.List;

public record TransactionReceipt(
        String status,
        String blockNumber,
        String from,
        String to,
        String contractAddress,
        String gasUsed,
        List<ReceiptLog> logs,
        String transactionHash
) {
}
