package com.wallet.txhistory.alchemy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AlchemyTransfer(
        String uniqueId,
        String hash,
        String from,
        String to,
        Double value,
        String asset,
        String category,
        String blockNum,
        Map<String, Object> rawContract,
        Map<String, Object> metadata,
        String tokenId
) {
    public String rawContractAddress() {
        return rawContract != null ? (String) rawContract.get("address") : null;
    }

    public Integer rawContractDecimals() {
        if (rawContract == null) return null;
        Object dec = rawContract.get("decimal");
        if (dec == null) dec = rawContract.get("decimals");
        if (dec instanceof Number n) return n.intValue();
        if (dec instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    public String rawContractValue() {
        return rawContract != null ? (String) rawContract.get("value") : null;
    }

    public long blockNumAsLong() {
        if (blockNum == null) return 0L;
        String hex = blockNum.startsWith("0x") ? blockNum.substring(2) : blockNum;
        return Long.parseLong(hex, 16);
    }
}
