package com.wallet.txhistory.dto;

import java.util.List;

public record ReceiptLog(
        String address,
        List<String> topics,
        String data,
        int logIndex,
        boolean removed
) {
}
