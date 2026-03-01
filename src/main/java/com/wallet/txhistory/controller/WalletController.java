package com.wallet.txhistory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.wallet.txhistory.dto.RegisterWalletRequest;
import com.wallet.txhistory.dto.SyncRequest;
import com.wallet.txhistory.dto.SyncResponse;
import com.wallet.txhistory.dto.UpdateWalletRequest;
import com.wallet.txhistory.dto.WalletResponse;
import com.wallet.txhistory.service.SyncService;
import com.wallet.txhistory.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/wallets")
@Tag(name = "Wallets", description = "Wallet registration and management")
public class WalletController {

    private final WalletService walletService;
    private final SyncService syncService;

    public WalletController(WalletService walletService, SyncService syncService) {
        this.walletService = walletService;
        this.syncService = syncService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new wallet")
    @ApiResponse(responseCode = "201", description = "Wallet registered")
    @ApiResponse(responseCode = "409", description = "Wallet already exists")
    public WalletResponse register(@Valid @RequestBody RegisterWalletRequest request) {
        return walletService.registerWallet(request);
    }

    @PatchMapping("/{walletId}")
    @Operation(summary = "Update wallet label")
    @ApiResponse(responseCode = "200", description = "Wallet updated")
    @ApiResponse(responseCode = "404", description = "Wallet not found")
    public WalletResponse updateLabel(@PathVariable UUID walletId,
                                       @Valid @RequestBody UpdateWalletRequest request) {
        return walletService.updateLabel(walletId, request);
    }

    @PostMapping("/{walletId}/sync")
    @Operation(summary = "Sync transaction history from Alchemy")
    @ApiResponse(responseCode = "200", description = "Sync completed")
    @ApiResponse(responseCode = "409", description = "Sync already in progress")
    @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    public SyncResponse sync(@PathVariable UUID walletId,
                              @Valid @RequestBody(required = false) SyncRequest request) {
        return syncService.sync(walletId, request);
    }
}
