package com.wallet.txhistory.alchemy;

import java.util.List;

public record JsonRpcRequest(
        String jsonrpc,
        int id,
        String method,
        List<AssetTransferParams> params
) {
    public static JsonRpcRequest of(AssetTransferParams params) {
        return new JsonRpcRequest("2.0", 1, "alchemy_getAssetTransfers", List.of(params));
    }
}
