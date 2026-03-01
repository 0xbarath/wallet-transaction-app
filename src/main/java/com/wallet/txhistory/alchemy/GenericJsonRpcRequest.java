package com.wallet.txhistory.alchemy;

import java.util.List;

public record GenericJsonRpcRequest(
        String jsonrpc,
        int id,
        String method,
        List<Object> params
) {
    public static GenericJsonRpcRequest of(String method, Object... params) {
        return new GenericJsonRpcRequest("2.0", 1, method, List.of(params));
    }
}
