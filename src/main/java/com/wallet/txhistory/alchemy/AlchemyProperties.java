package com.wallet.txhistory.alchemy;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.alchemy")
public record AlchemyProperties(
        String rpcUrl,
        String apiKey,
        List<String> categories,
        int maxCount,
        Duration timeout,
        int retryMaxAttempts
) {
    public AlchemyProperties {
        if (maxCount <= 0) maxCount = 1000;
        if (timeout == null) timeout = Duration.ofSeconds(30);
        if (retryMaxAttempts <= 0) retryMaxAttempts = 3;
        if (categories == null) categories = List.of("external", "internal", "erc20", "erc721", "erc1155");
    }

    public String resolveRpcUrl() {
        if (rpcUrl != null && !rpcUrl.isBlank()) {
            return rpcUrl;
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return "https://eth-mainnet.g.alchemy.com/v2/" + apiKey;
        }
        return null;
    }
}
