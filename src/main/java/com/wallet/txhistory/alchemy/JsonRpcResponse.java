package com.wallet.txhistory.alchemy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcResponse(
        String jsonrpc,
        int id,
        AssetTransferResult result,
        JsonRpcError error
) {
}
