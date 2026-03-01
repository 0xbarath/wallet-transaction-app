package com.wallet.txhistory.controller;

import com.wallet.txhistory.dto.ExplainRequest;
import com.wallet.txhistory.dto.ExplainResponse;
import com.wallet.txhistory.service.TransactionExplainService;
import com.wallet.txhistory.filter.RbacFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/transactions")
@Tag(name = "Transaction Enrichment", description = "Explain what a transaction does")
public class EnrichmentController {

    private final TransactionExplainService explainService;

    public EnrichmentController(TransactionExplainService explainService) {
        this.explainService = explainService;
    }

    @PostMapping("/explain")
    @Operation(summary = "Explain a transaction (admin-only)")
    @ApiResponse(responseCode = "200", description = "Transaction explained")
    @ApiResponse(responseCode = "403", description = "Admin role required")
    public ExplainResponse explain(@Valid @RequestBody ExplainRequest request,
                                    HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute(RbacFilter.ROLE_ATTRIBUTE);
        return explainService.explain(request, role);
    }
}
