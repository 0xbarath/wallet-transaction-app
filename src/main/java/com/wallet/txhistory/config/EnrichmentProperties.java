package com.wallet.txhistory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.Nullable;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.enrichment")
public record EnrichmentProperties(
        @Nullable String anthropicApiKey,
        String model,
        Duration timeout,
        int maxTokens
) {
    public EnrichmentProperties {
        if (model == null || model.isBlank()) model = "claude-sonnet-4-6-20250514";
        if (timeout == null) timeout = Duration.ofSeconds(30);
        if (maxTokens <= 0) maxTokens = 2048;
    }

    public boolean isLlmEnabled() {
        return anthropicApiKey != null && !anthropicApiKey.isBlank();
    }
}
