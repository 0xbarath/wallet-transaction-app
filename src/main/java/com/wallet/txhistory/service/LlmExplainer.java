package com.wallet.txhistory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.txhistory.config.EnrichmentProperties;
import com.wallet.txhistory.dto.ImmutableEvidenceItem;
import com.wallet.txhistory.dto.ImmutableExplanation;
import com.wallet.txhistory.dto.ImmutableExplanationStep;
import com.wallet.txhistory.dto.ImmutableOperationResult;
import com.wallet.txhistory.dto.ImmutableProtocolHint;
import com.wallet.txhistory.service.anthropic.AnthropicFeignClient;
import com.wallet.txhistory.service.anthropic.AnthropicRequest;
import com.wallet.txhistory.service.anthropic.AnthropicResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LlmExplainer {

    private static final Logger log = LoggerFactory.getLogger(LlmExplainer.class);

    private static final String SYSTEM_PROMPT = """
            You are a blockchain transaction explainer.

            RULES:
            1. You MUST only reference facts from the provided evidence bundle.
            2. Every claim you make MUST include an "evidenceIds" array citing specific evidence IDs from the bundle.
            3. If evidence is insufficient for any aspect, set text to "Unknown - insufficient evidence" and add what is missing to the "unknowns" array.
            4. Do NOT invent or infer addresses, amounts, protocols, token names, or operations not explicitly present in the evidence.
            5. Do NOT speculate about intent, profitability, or future actions.
            6. Respond with ONLY valid JSON matching the schema below. No markdown, no extra text.

            OUTPUT SCHEMA:
            {
              "summary": "One-sentence description of what happened",
              "steps": [{"text": "description of one step", "evidenceIds": ["ev:log:0", "ev:label:to"]}],
              "unknowns": ["list aspects where evidence is insufficient"],
              "safetyNotes": ["important caveats about this interpretation"]
            }""";

    private static final Pattern HEX_ADDRESS_PATTERN = Pattern.compile("0x[a-fA-F0-9]{40,64}");

    private final AnthropicFeignClient anthropicClient;
    private final EnrichmentProperties properties;
    private final ObjectMapper objectMapper;

    public LlmExplainer(AnthropicFeignClient anthropicClient,
                         EnrichmentProperties properties,
                         ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<ImmutableExplanation> explain(List<ImmutableEvidenceItem> evidence,
                                                   List<ImmutableProtocolHint> hints,
                                                   ImmutableOperationResult operation) {
        if (!properties.isLlmEnabled()) {
            return Optional.empty();
        }

        try {
            String userMessage = buildUserMessage(evidence, hints, operation);

            AnthropicRequest request = AnthropicRequest.of(
                    properties.model(),
                    properties.maxTokens(),
                    SYSTEM_PROMPT,
                    userMessage
            );

            AnthropicResponse response = anthropicClient.sendMessage(request);
            String text = response.firstText();
            if (text == null || text.isBlank()) {
                log.warn("Empty LLM response for transaction explanation");
                return Optional.empty();
            }

            return parseAndValidate(text, evidence, hints, operation);
        } catch (Exception e) {
            log.warn("LLM explanation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String buildUserMessage(List<ImmutableEvidenceItem> evidence,
                                     List<ImmutableProtocolHint> hints,
                                     ImmutableOperationResult operation) throws JsonProcessingException {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("evidence", evidence);
        bundle.put("protocolHints", hints);
        bundle.put("operation", operation);
        return objectMapper.writeValueAsString(bundle);
    }

    private Optional<ImmutableExplanation> parseAndValidate(String text,
                                                             List<ImmutableEvidenceItem> evidence,
                                                             List<ImmutableProtocolHint> hints,
                                                             ImmutableOperationResult operation) {
        // Strip markdown fences if present
        String json = text.strip();
        if (json.startsWith("```")) {
            int firstNewline = json.indexOf('\n');
            int lastFence = json.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                json = json.substring(firstNewline + 1, lastFence).strip();
            }
        }

        // Parse JSON
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            log.warn("LLM response is not valid JSON");
            return Optional.empty();
        }

        // Schema validation
        if (!root.has("summary") || !root.get("summary").isTextual()
                || !root.has("steps") || !root.get("steps").isArray()
                || !root.has("unknowns") || !root.get("unknowns").isArray()
                || !root.has("safetyNotes") || !root.get("safetyNotes").isArray()) {
            log.warn("LLM response does not match required schema");
            return Optional.empty();
        }

        // Build known evidence IDs
        Set<String> knownEvidenceIds = new HashSet<>();
        for (ImmutableEvidenceItem item : evidence) {
            knownEvidenceIds.add(item.id());
        }

        // Build known addresses/hashes from evidence
        Set<String> knownHexValues = new HashSet<>();
        for (ImmutableEvidenceItem item : evidence) {
            for (Object value : item.fields().values()) {
                if (value instanceof String s) {
                    extractHexValues(s, knownHexValues);
                }
            }
        }
        for (ImmutableProtocolHint hint : hints) {
            knownHexValues.add(hint.address().toLowerCase());
        }

        // Parse steps and validate citations
        List<ImmutableExplanationStep> steps = new ArrayList<>();
        for (JsonNode stepNode : root.get("steps")) {
            if (!stepNode.has("text") || !stepNode.has("evidenceIds")) {
                log.warn("Step missing required fields");
                return Optional.empty();
            }

            String stepText = stepNode.get("text").asText();
            List<String> stepEvidenceIds = new ArrayList<>();
            for (JsonNode eid : stepNode.get("evidenceIds")) {
                String evidenceId = eid.asText();
                if (!knownEvidenceIds.contains(evidenceId)) {
                    log.warn("LLM cited non-existent evidence ID: {}", evidenceId);
                    return Optional.empty();
                }
                stepEvidenceIds.add(evidenceId);
            }

            // Check for phantom addresses in step text
            Set<String> textHexValues = new HashSet<>();
            extractHexValues(stepText, textHexValues);
            for (String hex : textHexValues) {
                if (!knownHexValues.contains(hex.toLowerCase())) {
                    log.warn("LLM produced phantom address/hash: {}", hex);
                    return Optional.empty();
                }
            }

            steps.add(ImmutableExplanationStep.builder()
                    .text(stepText)
                    .evidenceIds(stepEvidenceIds)
                    .build());
        }

        // Parse unknowns and safety notes
        List<String> unknowns = new ArrayList<>();
        for (JsonNode u : root.get("unknowns")) {
            unknowns.add(u.asText());
        }
        List<String> safetyNotes = new ArrayList<>();
        for (JsonNode s : root.get("safetyNotes")) {
            safetyNotes.add(s.asText());
        }

        // Validate summary for phantom addresses
        String summary = root.get("summary").asText();
        Set<String> summaryHex = new HashSet<>();
        extractHexValues(summary, summaryHex);
        for (String hex : summaryHex) {
            if (!knownHexValues.contains(hex.toLowerCase())) {
                log.warn("LLM produced phantom address in summary: {}", hex);
                return Optional.empty();
            }
        }

        return Optional.of(ImmutableExplanation.builder()
                .summary(summary)
                .steps(steps)
                .unknowns(unknowns)
                .safetyNotes(safetyNotes)
                .build());
    }

    private static void extractHexValues(String text, Set<String> target) {
        Matcher matcher = HEX_ADDRESS_PATTERN.matcher(text);
        while (matcher.find()) {
            target.add(matcher.group().toLowerCase());
        }
    }
}
