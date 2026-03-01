package com.wallet.txhistory.alchemy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GenericJsonRpcResponse(
        String jsonrpc,
        int id,
        JsonNode result,
        JsonRpcError error
) {
}
