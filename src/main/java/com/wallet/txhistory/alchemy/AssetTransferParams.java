package com.wallet.txhistory.alchemy;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssetTransferParams(
        String fromAddress,
        String toAddress,
        String fromBlock,
        String toBlock,
        List<String> category,
        String maxCount,
        boolean withMetadata,
        String pageKey
) {
}
