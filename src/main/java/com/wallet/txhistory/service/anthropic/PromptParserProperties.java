package com.wallet.txhistory.service.anthropic;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.prompt-parser")
public record PromptParserProperties(
        String anthropicApiKey,
        String model,
        Duration timeout,
        int maxTokens
) {
    public PromptParserProperties {
        if (anthropicApiKey == null || anthropicApiKey.isBlank()) {
            throw new IllegalArgumentException("app.prompt-parser.anthropic-api-key must not be blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("app.prompt-parser.model must not be blank");
        }
        if (timeout == null) timeout = Duration.ofSeconds(30);
        if (maxTokens <= 0) maxTokens = 1024;
    }
}
