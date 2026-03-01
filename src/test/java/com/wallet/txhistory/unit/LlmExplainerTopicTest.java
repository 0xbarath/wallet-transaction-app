package com.wallet.txhistory.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.txhistory.config.EnrichmentProperties;
import com.wallet.txhistory.dto.ImmutableEvidenceItem;
import com.wallet.txhistory.dto.ImmutableExplanation;
import com.wallet.txhistory.dto.ImmutableOperationResult;
import com.wallet.txhistory.dto.ImmutableProtocolHint;
import com.wallet.txhistory.service.LlmExplainer;
import com.wallet.txhistory.service.anthropic.AnthropicContentBlock;
import com.wallet.txhistory.service.anthropic.AnthropicFeignClient;
import com.wallet.txhistory.service.anthropic.AnthropicResponse;
import com.wallet.txhistory.service.anthropic.AnthropicUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmExplainerTopicTest {

    @Mock
    private AnthropicFeignClient anthropicClient;

    private LlmExplainer explainer;
    private List<ImmutableProtocolHint> hints;
    private ImmutableOperationResult operation;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties(
                "test-api-key", "claude-sonnet-4-6-20250514", Duration.ofSeconds(30), 2048);
        explainer = new LlmExplainer(anthropicClient, properties, new ObjectMapper());

        hints = List.of(ImmutableProtocolHint.builder()
                .address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .protocol("uniswap-v3").label("Uniswap V3: Router")
                .confidence(0.99).source("curated")
                .build());

        operation = ImmutableOperationResult.builder()
                .name("swap").confidence(0.9).evidenceIds(List.of("ev:log:0")).build();
    }

    @Test
    void acceptsAddressDerivedFromZeroPaddedTopic() {
        List<ImmutableEvidenceItem> evidence = List.of(
                ImmutableEvidenceItem.builder()
                        .id("ev:tx")
                        .type("tx")
                        .fields(Map.of(
                                "from", "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                "to", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                        .build(),
                ImmutableEvidenceItem.builder()
                        .id("ev:log:0")
                        .type("log")
                        .fields(Map.of(
                                "address", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                "topics", List.of(
                                        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                                        "0x000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")))
                        .build()
        );

        // LLM references the unpadded 20-byte form derived from the zero-padded topic
        String llmResponse = """
                {
                  "summary": "Swap via Uniswap involving token 0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                  "steps": [{"text": "Token 0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48 was transferred", "evidenceIds": ["ev:log:0"]}],
                  "unknowns": [],
                  "safetyNotes": ["Standard swap"]
                }""";
        mockAnthropicResponse(llmResponse);

        Optional<ImmutableExplanation> result = explainer.explain(evidence, hints, operation);

        assertThat(result).isPresent();
        assertThat(result.get().summary()).contains("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
    }

    @Test
    void rejectsAddressNotInTopicsOrFields() {
        List<ImmutableEvidenceItem> evidence = List.of(
                ImmutableEvidenceItem.builder()
                        .id("ev:tx")
                        .type("tx")
                        .fields(Map.of(
                                "from", "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                "to", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                        .build(),
                ImmutableEvidenceItem.builder()
                        .id("ev:log:0")
                        .type("log")
                        .fields(Map.of(
                                "address", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                "topics", List.of(
                                        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef")))
                        .build()
        );

        // LLM references a completely fabricated address
        String llmResponse = """
                {
                  "summary": "Sent to 0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                  "steps": [{"text": "Transfer", "evidenceIds": ["ev:tx"]}],
                  "unknowns": [],
                  "safetyNotes": []
                }""";
        mockAnthropicResponse(llmResponse);

        Optional<ImmutableExplanation> result = explainer.explain(evidence, hints, operation);

        assertThat(result).isEmpty();
    }

    @Test
    void acceptsFullPaddedTopicReference() {
        List<ImmutableEvidenceItem> evidence = List.of(
                ImmutableEvidenceItem.builder()
                        .id("ev:tx")
                        .type("tx")
                        .fields(Map.of(
                                "from", "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                "to", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                        .build(),
                ImmutableEvidenceItem.builder()
                        .id("ev:log:0")
                        .type("log")
                        .fields(Map.of(
                                "address", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                "topics", List.of(
                                        "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                                        "0x000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48")))
                        .build()
        );

        // LLM references the full 32-byte padded topic value
        String llmResponse = """
                {
                  "summary": "Swap via Uniswap",
                  "steps": [{"text": "Transfer event 0x000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48 observed", "evidenceIds": ["ev:log:0"]}],
                  "unknowns": [],
                  "safetyNotes": ["Standard swap"]
                }""";
        mockAnthropicResponse(llmResponse);

        Optional<ImmutableExplanation> result = explainer.explain(evidence, hints, operation);

        assertThat(result).isPresent();
    }

    private void mockAnthropicResponse(String text) {
        AnthropicResponse response = new AnthropicResponse(
                "msg-123", "message", "claude-sonnet-4-6-20250514", "end_turn",
                List.of(new AnthropicContentBlock("text", text)),
                new AnthropicUsage(100, 200)
        );
        when(anthropicClient.sendMessage(any())).thenReturn(response);
    }
}
