package com.wallet.txhistory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import com.wallet.txhistory.dto.PageResponse;
import com.wallet.txhistory.dto.PromptQueryRequest;
import com.wallet.txhistory.dto.TransferResponse;
import com.wallet.txhistory.service.PromptParserService;
import com.wallet.txhistory.service.TransactionQueryService;
import com.wallet.txhistory.model.Direction;
import com.wallet.txhistory.model.ImmutableQuerySpec;
import com.wallet.txhistory.model.TransferCategory;
import com.wallet.txhistory.model.QuerySpec;
import com.wallet.txhistory.filter.RbacFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/transactions")
@Tag(name = "Transactions", description = "Transaction history queries")
public class TransactionController {

    private final TransactionQueryService queryService;
    private final PromptParserService promptParserService;

    public TransactionController(TransactionQueryService queryService, PromptParserService promptParserService) {
        this.queryService = queryService;
        this.promptParserService = promptParserService;
    }

    @GetMapping
    @Operation(summary = "Query transactions with filters")
    public PageResponse<TransferResponse> query(
            @RequestParam UUID walletId,
            @RequestParam(required = false) Direction direction,
            @RequestParam(required = false) List<TransferCategory> category,
            @RequestParam(required = false) List<String> asset,
            @RequestParam(required = false) BigDecimal minValue,
            @RequestParam(required = false) BigDecimal maxValue,
            @RequestParam(required = false) String counterparty,
            @RequestParam(required = false) OffsetDateTime startTime,
            @RequestParam(required = false) OffsetDateTime endTime,
            @RequestParam(required = false, defaultValue = "createdAt_desc") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "50") int limit,
            HttpServletRequest request) {

        String role = (String) request.getAttribute(RbacFilter.ROLE_ATTRIBUTE);

        ImmutableQuerySpec.Builder builder = ImmutableQuerySpec.builder()
                .walletId(walletId)
                .direction(direction)
                .minValue(minValue)
                .maxValue(maxValue)
                .counterparty(counterparty)
                .startTime(startTime)
                .endTime(endTime)
                .sort(sort)
                .cursor(cursor)
                .limit(limit);
        if (category != null) builder.categories(category);
        if (asset != null) builder.assets(asset);
        QuerySpec spec = builder.build();

        return queryService.query(spec, role);
    }

    @PostMapping(":query")
    @Operation(summary = "Query transactions with natural language prompt")
    public PageResponse<TransferResponse> promptQuery(
            @Valid @RequestBody PromptQueryRequest request,
            HttpServletRequest httpRequest) {

        String role = (String) httpRequest.getAttribute(RbacFilter.ROLE_ATTRIBUTE);

        QuerySpec spec = promptParserService.parse(request.prompt(), request.walletId(), role);

        // Override limit and cursor from request if provided
        if (request.limit() != null || request.cursor() != null) {
            ImmutableQuerySpec copy = ImmutableQuerySpec.copyOf(spec);
            if (request.cursor() != null) {
                copy = copy.withCursor(request.cursor());
            }
            if (request.limit() != null) {
                copy = copy.withLimit(request.limit());
            }
            spec = copy;
        }

        return queryService.query(spec, role);
    }
}
