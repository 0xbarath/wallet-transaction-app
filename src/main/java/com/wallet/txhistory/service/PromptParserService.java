package com.wallet.txhistory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.txhistory.exception.ForbiddenCategoryException;
import com.wallet.txhistory.filter.RbacFilter;
import com.wallet.txhistory.exception.PromptParseException;
import com.wallet.txhistory.model.Direction;
import com.wallet.txhistory.model.ImmutableQuerySpec;
import com.wallet.txhistory.model.QuerySpec;
import com.wallet.txhistory.model.TransferCategory;
import com.wallet.txhistory.service.anthropic.AnthropicFeignClient;
import com.wallet.txhistory.service.anthropic.AnthropicRequest;
import com.wallet.txhistory.service.anthropic.AnthropicResponse;
import com.wallet.txhistory.service.anthropic.LlmParsedQuery;
import com.wallet.txhistory.service.anthropic.PromptParserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PromptParserService {

    private static final Logger log = LoggerFactory.getLogger(PromptParserService.class);
    private static final Pattern MARKDOWN_FENCE = Pattern.compile("^```(?:json)?\\s*\\n?|\\n?```$");
    private static final Pattern ETH_ADDRESS = Pattern.compile("^0x[a-fA-F0-9]{40}$");
    private static final Pattern ASSET_PATTERN = Pattern.compile("^[A-Z0-9]{1,20}$");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final int MAX_PROMPT_LENGTH = 2000;
    private static final int MAX_ASSETS = 10;
    private static final int MAX_CLARIFICATIONS = 5;
    private static final int MAX_CLARIFICATION_LENGTH = 200;
    private static final int MAX_PRECISION = 38;
    private static final int MAX_SCALE = 18;

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a structured data extractor for a wallet transaction query system.
            Today's date is %s.

            Given a natural-language prompt about wallet transactions, extract the query parameters as JSON.

            Return ONLY a JSON object with these fields (use null for absent values):
            {
              "direction": "IN" | "OUT" | null,
              "categories": ["EXTERNAL","INTERNAL","ERC20","ERC721","ERC1155"] or [],
              "assets": ["ETH","USDC",...] or [],
              "minValue": "numeric string" | null,
              "maxValue": "numeric string" | null,
              "counterparty": "0x... address" | null,
              "startTime": "ISO-8601 datetime with offset" | null,
              "endTime": "ISO-8601 datetime with offset" | null,
              "sort": "createdAt_desc" | "createdAt_asc" | null,
              "limit": integer | null,
              "needsClarification": ["question1", ...] or []
            }

            Rules:
            - "incoming", "received", "deposits" → direction "IN"
            - "outgoing", "sent", "withdrawals" → direction "OUT"
            - "nft" maps to category "ERC721"
            - Resolve relative times (e.g., "last 7 days", "yesterday", "last month") to ISO-8601 datetimes in UTC
            - Asset names should be UPPERCASE (e.g., "ETH", "USDC")
            - If the prompt is ambiguous or you cannot determine the intent, populate "needsClarification" with specific questions
            - Do NOT wrap the JSON in markdown fences
            """;

    private final AnthropicFeignClient anthropicClient;
    private final PromptParserProperties properties;
    private final ObjectMapper objectMapper;

    public PromptParserService(AnthropicFeignClient anthropicClient,
                               PromptParserProperties properties,
                               ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public QuerySpec parse(String prompt, UUID walletId, String role) {
        if (prompt != null && prompt.length() > MAX_PROMPT_LENGTH) {
            throw new PromptParseException("Prompt exceeds maximum length of " + MAX_PROMPT_LENGTH + " characters");
        }

        String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, LocalDate.now());

        AnthropicRequest request = AnthropicRequest.of(
                properties.model(),
                properties.maxTokens(),
                systemPrompt,
                prompt
        );

        AnthropicResponse response = anthropicClient.sendMessage(request);

        String text = response.firstText();
        if (text == null || text.isBlank()) {
            throw new PromptParseException("LLM returned empty response");
        }

        // Strip markdown fences if present
        text = MARKDOWN_FENCE.matcher(text.strip()).replaceAll("").strip();

        LlmParsedQuery parsed;
        try {
            parsed = objectMapper.readValue(text, LlmParsedQuery.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse LLM JSON response: {}", sanitizeForLog(text, 500), e);
            throw new PromptParseException("Failed to parse LLM response as JSON");
        }

        if (parsed.needsClarification() != null && !parsed.needsClarification().isEmpty()) {
            List<String> sanitized = sanitizeNeedsClarification(parsed.needsClarification());
            throw new PromptParseException("Prompt needs clarification", sanitized);
        }

        return mapToQuerySpec(parsed, walletId, role);
    }

    private QuerySpec mapToQuerySpec(LlmParsedQuery parsed, UUID walletId, String role) {
        Direction direction = parseDirection(parsed.direction());
        BigDecimal minValue = parseBigDecimal(parsed.minValue(), "minValue");
        BigDecimal maxValue = parseBigDecimal(parsed.maxValue(), "maxValue");
        List<String> assets = parseAssets(parsed.assets());
        List<TransferCategory> categories = parseCategories(parsed.categories());
        String counterparty = parseCounterparty(parsed.counterparty());
        OffsetDateTime startTime = parseDateTime(parsed.startTime(), "startTime");
        OffsetDateTime endTime = parseDateTime(parsed.endTime(), "endTime");
        String sort = parseSort(parsed.sort());
        int limit = parseLimit(parsed.limit());

        boolean isAdmin = RbacFilter.isAdmin(role);
        if (!isAdmin && categories.contains(TransferCategory.INTERNAL)) {
            throw new ForbiddenCategoryException("INTERNAL transfers are only accessible to admins");
        }

        return ImmutableQuerySpec.builder()
                .walletId(walletId)
                .direction(direction)
                .categories(categories)
                .assets(assets)
                .minValue(minValue)
                .maxValue(maxValue)
                .counterparty(counterparty)
                .startTime(startTime)
                .endTime(endTime)
                .sort(sort)
                .limit(limit)
                .build();
    }

    private Direction parseDirection(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Direction.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid direction from LLM: {}", sanitizeForLog(value, 100));
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            BigDecimal bd = new BigDecimal(value);
            if (bd.precision() > MAX_PRECISION || Math.abs(bd.scale()) > MAX_SCALE) {
                log.warn("Out-of-bounds {} from LLM: precision={} scale={}", field, bd.precision(), bd.scale());
                return null;
            }
            if (bd.signum() < 0) {
                log.warn("Negative {} from LLM: {}", field, sanitizeForLog(value, 100));
                return null;
            }
            return bd;
        } catch (NumberFormatException e) {
            log.warn("Invalid {} from LLM: {}", field, sanitizeForLog(value, 100));
            return null;
        }
    }

    private List<String> parseAssets(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String asset : values) {
            if (asset == null || asset.isBlank()) continue;
            String upper = asset.toUpperCase();
            if (!ASSET_PATTERN.matcher(upper).matches()) {
                log.warn("Invalid asset name from LLM, skipping: {}", sanitizeForLog(asset, 100));
                continue;
            }
            result.add(upper);
            if (result.size() >= MAX_ASSETS) break;
        }
        return result;
    }

    private List<TransferCategory> parseCategories(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        List<TransferCategory> result = new ArrayList<>();
        for (String cat : values) {
            if (cat == null || cat.isBlank()) continue;
            try {
                TransferCategory tc = TransferCategory.valueOf(cat.toUpperCase());
                if (!result.contains(tc)) {
                    result.add(tc);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid category from LLM: {}", sanitizeForLog(cat, 100));
            }
        }
        return result;
    }

    private String parseCounterparty(String value) {
        if (value == null || value.isBlank()) return null;
        if (ETH_ADDRESS.matcher(value).matches()) {
            return value;
        }
        log.warn("Invalid counterparty address from LLM: {}", sanitizeForLog(value, 100));
        return null;
    }

    private OffsetDateTime parseDateTime(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            log.warn("Invalid {} from LLM: {}", field, sanitizeForLog(value, 100));
            return null;
        }
    }

    private String parseSort(String value) {
        if (QuerySpec.SORT_CREATED_AT_ASC.equals(value)) return value;
        return QuerySpec.SORT_CREATED_AT_DESC;
    }

    private int parseLimit(Integer value) {
        if (value == null || value <= 0) return QuerySpec.DEFAULT_LIMIT;
        return Math.min(value, QuerySpec.MAX_LIMIT);
    }

    private List<String> sanitizeNeedsClarification(List<String> items) {
        List<String> result = new ArrayList<>();
        for (String item : items) {
            if (item == null) continue;
            String sanitized = HTML_TAG.matcher(item).replaceAll("");
            sanitized = stripControlChars(sanitized);
            if (sanitized.length() > MAX_CLARIFICATION_LENGTH) {
                sanitized = sanitized.substring(0, MAX_CLARIFICATION_LENGTH);
            }
            result.add(sanitized);
            if (result.size() >= MAX_CLARIFICATIONS) break;
        }
        return result;
    }

    private static String sanitizeForLog(String value, int maxLength) {
        if (value == null) return "null";
        String sanitized = stripControlChars(value);
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength) + "...";
        }
        return sanitized;
    }

    private static String stripControlChars(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 && c != ' ') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
