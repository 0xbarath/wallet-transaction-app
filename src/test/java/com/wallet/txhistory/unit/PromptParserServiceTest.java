package com.wallet.txhistory.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.txhistory.exception.ForbiddenCategoryException;
import com.wallet.txhistory.exception.PromptParseException;
import com.wallet.txhistory.model.Direction;
import com.wallet.txhistory.model.QuerySpec;
import com.wallet.txhistory.model.TransferCategory;
import com.wallet.txhistory.service.PromptParserService;
import com.wallet.txhistory.service.anthropic.AnthropicContentBlock;
import com.wallet.txhistory.service.anthropic.AnthropicFeignClient;
import com.wallet.txhistory.service.anthropic.AnthropicRequest;
import com.wallet.txhistory.service.anthropic.AnthropicResponse;
import com.wallet.txhistory.service.anthropic.AnthropicUsage;
import com.wallet.txhistory.service.anthropic.PromptParserProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptParserServiceTest {

    private AnthropicFeignClient anthropicClient;
    private PromptParserService parser;
    private final UUID walletId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        anthropicClient = mock(AnthropicFeignClient.class);
        PromptParserProperties properties = new PromptParserProperties(
                "test-key", "claude-sonnet-4-6-20250514", Duration.ofSeconds(5), 1024
        );
        parser = new PromptParserService(anthropicClient, properties, objectMapper);
    }

    @Test
    void parsesOutgoingDirection() {
        stubLlmResponse("""
                {"direction":"OUT","categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show outgoing transactions", walletId, "user");
        assertThat(spec.direction()).isEqualTo(Direction.OUT);
    }

    @Test
    void parsesIncomingDirection() {
        stubLlmResponse("""
                {"direction":"IN","categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show received transactions", walletId, "user");
        assertThat(spec.direction()).isEqualTo(Direction.IN);
    }

    @Test
    void parsesNoDirection() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show all transactions", walletId, "user");
        assertThat(spec.direction()).isNull();
    }

    @Test
    void parsesAmountRange() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":"100","maxValue":"500",\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("transactions between 100 and 500", walletId, "user");
        assertThat(spec.minValue()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(spec.maxValue()).isEqualByComparingTo(new BigDecimal("500"));
    }

    @Test
    void parsesAssetsAndCategories() {
        stubLlmResponse("""
                {"direction":"OUT","categories":["ERC20"],"assets":["USDC","ETH"],"minValue":"100","maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show outgoing USDC and ETH ERC20 above 100", walletId, "user");
        assertThat(spec.direction()).isEqualTo(Direction.OUT);
        assertThat(spec.assets()).containsExactlyInAnyOrder("USDC", "ETH");
        assertThat(spec.categories()).contains(TransferCategory.ERC20);
        assertThat(spec.minValue()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    void parsesCounterpartyAddress() {
        String addr = "0x1234567890abcdef1234567890abcdef12345678";
        stubLlmResponse("""
                {"direction":"OUT","categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":"%s","startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""".formatted(addr));

        QuerySpec spec = parser.parse("sent to " + addr, walletId, "user");
        assertThat(spec.counterparty()).isEqualTo(addr);
    }

    @Test
    void invalidCounterpartyIsSkipped() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":"not-an-address","startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("sent to someone", walletId, "user");
        assertThat(spec.counterparty()).isNull();
    }

    @Test
    void parsesTimeRange() {
        String start = "2026-02-21T00:00:00Z";
        String end = "2026-02-28T00:00:00Z";
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":"%s","endTime":"%s","sort":null,"limit":null,"needsClarification":[]}""".formatted(start, end));

        QuerySpec spec = parser.parse("show last 7 days", walletId, "user");
        assertThat(spec.startTime()).isEqualTo(OffsetDateTime.parse(start));
        assertThat(spec.endTime()).isEqualTo(OffsetDateTime.parse(end));
    }

    @Test
    void adminCanRequestInternal() {
        stubLlmResponse("""
                {"direction":null,"categories":["INTERNAL"],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show internal transactions", walletId, "admin");
        assertThat(spec.categories()).contains(TransferCategory.INTERNAL);
    }

    @Test
    void userCannotRequestInternal() {
        stubLlmResponse("""
                {"direction":null,"categories":["INTERNAL"],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        assertThatThrownBy(() -> parser.parse("show internal transactions", walletId, "user"))
                .isInstanceOf(ForbiddenCategoryException.class);
    }

    @Test
    void setsDefaultLimitAndSort() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show all", walletId, "user");
        assertThat(spec.limit()).isEqualTo(QuerySpec.DEFAULT_LIMIT);
        assertThat(spec.sort()).isEqualTo(QuerySpec.SORT_CREATED_AT_DESC);
    }

    @Test
    void respectsCustomSortAndLimit() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":"createdAt_asc","limit":10,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show oldest 10 transactions", walletId, "user");
        assertThat(spec.sort()).isEqualTo(QuerySpec.SORT_CREATED_AT_ASC);
        assertThat(spec.limit()).isEqualTo(10);
    }

    @Test
    void needsClarificationThrowsPromptParseException() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,\
                "needsClarification":["Do you mean incoming or outgoing?","Which asset?"]}""");

        assertThatThrownBy(() -> parser.parse("show stuff", walletId, "user"))
                .isInstanceOf(PromptParseException.class)
                .satisfies(ex -> {
                    PromptParseException ppe = (PromptParseException) ex;
                    assertThat(ppe.getNeedsClarification()).containsExactly(
                            "Do you mean incoming or outgoing?", "Which asset?");
                });
    }

    @Test
    void emptyResponseThrowsPromptParseException() {
        AnthropicResponse emptyResponse = new AnthropicResponse(
                "msg_1", "message", "claude-sonnet-4-6-20250514", "end_turn",
                List.of(), new AnthropicUsage(10, 0));
        when(anthropicClient.sendMessage(any())).thenReturn(emptyResponse);

        assertThatThrownBy(() -> parser.parse("show all", walletId, "user"))
                .isInstanceOf(PromptParseException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void invalidJsonThrowsPromptParseException() {
        stubLlmResponse("this is not json at all");

        assertThatThrownBy(() -> parser.parse("show all", walletId, "user"))
                .isInstanceOf(PromptParseException.class)
                .hasMessageContaining("Failed to parse LLM response");
    }

    @Test
    void markdownFencedJsonIsHandled() {
        stubLlmResponse("""
                ```json
                {"direction":"IN","categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}
                ```""");

        QuerySpec spec = parser.parse("show incoming", walletId, "user");
        assertThat(spec.direction()).isEqualTo(Direction.IN);
    }

    @Test
    void promptIsPassedCorrectlyToLlm() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        parser.parse("show my ETH deposits from last week", walletId, "user");

        ArgumentCaptor<AnthropicRequest> captor = ArgumentCaptor.forClass(AnthropicRequest.class);
        verify(anthropicClient).sendMessage(captor.capture());

        AnthropicRequest captured = captor.getValue();
        assertThat(captured.model()).isEqualTo("claude-sonnet-4-6-20250514");
        assertThat(captured.maxTokens()).isEqualTo(1024);
        assertThat(captured.system()).contains("structured data extractor");
        assertThat(captured.messages()).hasSize(1);
        assertThat(captured.messages().getFirst().role()).isEqualTo("user");
        assertThat(captured.messages().getFirst().content()).isEqualTo("show my ETH deposits from last week");
    }

    // --- Security-focused tests ---

    @Test
    void rejectsPromptExceedingMaxLength() {
        String longPrompt = "a".repeat(2001);

        assertThatThrownBy(() -> parser.parse(longPrompt, walletId, "user"))
                .isInstanceOf(PromptParseException.class)
                .hasMessageContaining("maximum length");
    }

    @Test
    void rejectsAssetWithSpecialCharacters() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":["'; DROP TABLE","ETH","<script>"],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show transactions", walletId, "user");
        assertThat(spec.assets()).containsExactly("ETH");
    }

    @Test
    void rejectsAssetExceedingMaxLength() {
        String longAsset = "A".repeat(21);
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":["%s","ETH"],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""".formatted(longAsset));

        QuerySpec spec = parser.parse("show transactions", walletId, "user");
        assertThat(spec.assets()).containsExactly("ETH");
    }

    @Test
    void capsAssetListSize() {
        String assets = IntStream.rangeClosed(1, 15)
                .mapToObj(i -> "\"T" + i + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":%s,"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""".formatted(assets));

        QuerySpec spec = parser.parse("show transactions", walletId, "user");
        assertThat(spec.assets()).hasSize(10);
    }

    @Test
    void rejectsExtremeBigDecimalScale() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":"1E+999999999","maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show large transactions", walletId, "user");
        assertThat(spec.minValue()).isNull();
    }

    @Test
    void rejectsNegativeBigDecimal() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":"-100","maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,"needsClarification":[]}""");

        QuerySpec spec = parser.parse("show transactions above -100", walletId, "user");
        assertThat(spec.minValue()).isNull();
    }

    @Test
    void sanitizesNeedsClarificationContent() {
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,\
                "needsClarification":["<script>alert('xss')</script>Do you mean ETH?","%s"]}"""
                .formatted("A".repeat(300)));

        assertThatThrownBy(() -> parser.parse("show stuff", walletId, "user"))
                .isInstanceOf(PromptParseException.class)
                .satisfies(ex -> {
                    PromptParseException ppe = (PromptParseException) ex;
                    List<String> clarifications = ppe.getNeedsClarification();
                    assertThat(clarifications.get(0)).doesNotContain("<script>");
                    assertThat(clarifications.get(0)).contains("Do you mean ETH?");
                    assertThat(clarifications.get(1)).hasSizeLessThanOrEqualTo(200);
                });
    }

    @Test
    void capsNeedsClarificationListSize() {
        String items = IntStream.rangeClosed(1, 8)
                .mapToObj(i -> "\"Question " + i + "?\"")
                .collect(Collectors.joining(",", "[", "]"));
        stubLlmResponse("""
                {"direction":null,"categories":[],"assets":[],"minValue":null,"maxValue":null,\
                "counterparty":null,"startTime":null,"endTime":null,"sort":null,"limit":null,\
                "needsClarification":%s}""".formatted(items));

        assertThatThrownBy(() -> parser.parse("show stuff", walletId, "user"))
                .isInstanceOf(PromptParseException.class)
                .satisfies(ex -> {
                    PromptParseException ppe = (PromptParseException) ex;
                    assertThat(ppe.getNeedsClarification()).hasSize(5);
                });
    }

    private void stubLlmResponse(String jsonText) {
        AnthropicResponse response = new AnthropicResponse(
                "msg_1", "message", "claude-sonnet-4-6-20250514", "end_turn",
                List.of(new AnthropicContentBlock("text", jsonText)),
                new AnthropicUsage(100, 50)
        );
        when(anthropicClient.sendMessage(any())).thenReturn(response);
    }
}
