package com.wallet.txhistory.service.anthropic;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record AnthropicRequest(
        String model,
        @JsonProperty("max_tokens") int maxTokens,
        String system,
        List<AnthropicMessage> messages
) {
    public static AnthropicRequest of(String model, int maxTokens, String systemPrompt, String userPrompt) {
        return new AnthropicRequest(
                model,
                maxTokens,
                systemPrompt,
                List.of(new AnthropicMessage("user", userPrompt))
        );
    }
}
