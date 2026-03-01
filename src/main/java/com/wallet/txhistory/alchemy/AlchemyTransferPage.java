package com.wallet.txhistory.alchemy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlchemyTransferPage(
        List<AlchemyTransfer> transfers,
        String pageKey
) {
    public boolean hasMore() {
        return pageKey != null && !pageKey.isBlank();
    }
}
