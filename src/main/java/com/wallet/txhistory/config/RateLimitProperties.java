package com.wallet.txhistory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        double tokensPerSecond,
        int capacity
) {
    public RateLimitProperties {
        if (tokensPerSecond <= 0) tokensPerSecond = 2.0;
        if (capacity <= 0) capacity = 5;
    }
}
