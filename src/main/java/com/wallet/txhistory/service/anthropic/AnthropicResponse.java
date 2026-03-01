package com.wallet.txhistory.service.anthropic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicResponse(
        String id,
        String type,
        String model,
        @JsonProperty("stop_reason") String stopReason,
        List<AnthropicContentBlock> content,
        AnthropicUsage usage
) {
    public String firstText() {
        if (content == null || content.isEmpty()) {
            return null;
        }
        return content.stream()
                .filter(b -> "text".equals(b.type()))
                .map(AnthropicContentBlock::text)
                .findFirst()
                .orElse(null);
    }
}
