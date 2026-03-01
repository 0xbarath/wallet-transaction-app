package com.wallet.txhistory.alchemy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcError(
        int code,
        String message
) {
}
