package com.wallet.txhistory.service.anthropic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicContentBlock(String type, String text) {
}
