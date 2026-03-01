package com.wallet.txhistory.alchemy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetTransferResult(
        List<AlchemyTransfer> transfers,
        String pageKey
) {
}
