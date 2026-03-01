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
class EvidenceValidationTest {

    @Mock
    private AnthropicFeignClient anthropicClient;

    private LlmExplainer explainer;
    private List<ImmutableEvidenceItem> evidence;
    private List<ImmutableProtocolHint> hints;
    private ImmutableOperationResult operation;

    @BeforeEach
    void setUp() {
        EnrichmentProperties properties = new EnrichmentProperties(
                "test-api-key", "claude-sonnet-4-6-20250514", Duration.ofSeconds(30), 2048);
        explainer = new LlmExplainer(anthropicClient, properties, new ObjectMapper());

        evidence = List.of(
                ImmutableEvidenceItem.builder()
                        .id("ev:tx")
                        .type("tx")
                        .fields(Map.of(
                                "from", "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                "to", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                "status", "0x1"))
                        .build(),
                ImmutableEvidenceItem.builder()
                        .id("ev:log:0")
                        .type("log")
                        .fields(Map.of("address", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                        .build()
        );

        hints = List.of(ImmutableProtocolHint.builder()
                .address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                .protocol("aave-v3").label("Aave V3: Pool")
                .confidence(0.99).source("curated")
                .build());

        operation = ImmutableOperationResult.builder()
                .name("aave_supply").confidence(0.9).evidenceIds(List.of("ev:log:0")).build();
    }

    @Test
    void validExplanationPassesValidation() {
        String llmResponse = """
                {
                  "summary": "Transaction sent to 0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                  "steps": [{"text": "Sent from 0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "evidenceIds": ["ev:tx"]}],
                  "unknowns": [],
                  "safetyNotes": ["Standard transaction"]
                }""";
        mockAnthropicResponse(llmResponse);

        Optional<ImmutableExplanation> result = explainer.explain(evidence, hints, operation);

        assertThat(result).isPresent();
        assertThat(result.get().summary()).contains("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
    }

    @Test
    void rejectsNonExistentEvidenceId() {
        String llmResponse = """
                {
                  "summary": "Some transaction",
                  "steps": [{"text": "Something happened", "evidenceIds": ["ev:log:99"]}],
                  "unknowns": [],
                  "safetyNotes": []
                }""";
        mockAnthropicResponse(llmResponse);

        Optional<ImmutableExplanation> result = explainer.explain(evidence, hints, operation);

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsPhantomAddress() {
        String llmResponse = """
                {
                  "summary": "Sent to 0xcccccccccccccccccccccccccccccccccccccccc",
                  "steps": [{"text": "Transfer", "evidenceIds": ["ev:tx"]}],
                  "unknowns": [],
                  "safetyNotes": []
                }""";
        mockAnthropicResponse(llmResponse);

        Optional<ImmutableExplanation> result = explainer.explain(evidence, hints, operation);

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenLlmDisabled() {
        EnrichmentProperties disabled = new EnrichmentProperties(
                null, "claude-sonnet-4-6-20250514", Duration.ofSeconds(30), 2048);
        LlmExplainer disabledExplainer = new LlmExplainer(anthropicClient, disabled, new ObjectMapper());

        Optional<ImmutableExplanation> result = disabledExplainer.explain(evidence, hints, operation);

        assertThat(result).isEmpty();
    }

    @Test
    void acceptsAddressFromAbiEncodedDataField() {
        // Data field with two 32-byte words: zero-padded USDC address + zero-padded recipient
        String abiData = "0x"
                + "000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"  // USDC
                + "000000000000000000000000dead0001dead0001dead0001dead0001dead0001";  // recipient
        List<ImmutableEvidenceItem> abiEvidence = List.of(
                ImmutableEvidenceItem.builder()
                        .id("ev:tx")
                        .type("tx")
                        .fields(Map.of("from", "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                       "to", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                        .build(),
                ImmutableEvidenceItem.builder()
                        .id("ev:log:0")
                        .type("log")
                        .fields(Map.of("address", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                       "data", abiData))
                        .build()
        );

        // LLM mentions the address from the *second* ABI word
        String llmResponse = """
                {
                  "summary": "Transfer to 0xdead0001dead0001dead0001dead0001dead0001",
                  "steps": [{"text": "Tokens sent to 0xdead0001dead0001dead0001dead0001dead0001", "evidenceIds": ["ev:log:0"]}],
                  "unknowns": [],
                  "safetyNotes": []
                }""";
        mockAnthropicResponse(llmResponse);

        Optional<ImmutableExplanation> result = explainer.explain(abiEvidence, hints, operation);

        assertThat(result).isPresent();
    }

    @Test
    void rejectsPhantomAddressNotInAbiData() {
        // Data field with one 32-byte word
        String abiData = "0x"
                + "000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";
        List<ImmutableEvidenceItem> abiEvidence = List.of(
                ImmutableEvidenceItem.builder()
                        .id("ev:tx")
                        .type("tx")
                        .fields(Map.of("from", "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                       "to", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"))
                        .build(),
                ImmutableEvidenceItem.builder()
                        .id("ev:log:0")
                        .type("log")
                        .fields(Map.of("address", "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                                       "data", abiData))
                        .build()
        );

        // LLM mentions an address NOT in any ABI word
        String llmResponse = """
                {
                  "summary": "Transfer to 0xdead0001dead0001dead0001dead0001dead0001",
                  "steps": [{"text": "Tokens sent to 0xdead0001dead0001dead0001dead0001dead0001", "evidenceIds": ["ev:log:0"]}],
                  "unknowns": [],
                  "safetyNotes": []
                }""";
        mockAnthropicResponse(llmResponse);

        Optional<ImmutableExplanation> result = explainer.explain(abiEvidence, hints, operation);

        assertThat(result).isEmpty();
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
