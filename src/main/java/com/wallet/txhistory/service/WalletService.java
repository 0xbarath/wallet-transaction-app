package com.wallet.txhistory.service;

import com.wallet.txhistory.dto.ImmutableWalletResponse;
import com.wallet.txhistory.dto.RegisterWalletRequest;
import com.wallet.txhistory.dto.UpdateWalletRequest;
import com.wallet.txhistory.dto.WalletResponse;
import com.wallet.txhistory.exception.DuplicateWalletException;
import com.wallet.txhistory.exception.WalletNotFoundException;
import com.wallet.txhistory.model.Wallet;
import com.wallet.txhistory.model.WalletSyncState;
import com.wallet.txhistory.repository.WalletRepository;
import com.wallet.txhistory.repository.WalletSyncStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletSyncStateRepository syncStateRepository;

    public WalletService(WalletRepository walletRepository, WalletSyncStateRepository syncStateRepository) {
        this.walletRepository = walletRepository;
        this.syncStateRepository = syncStateRepository;
    }

    @Transactional
    public WalletResponse registerWallet(RegisterWalletRequest request) {
        String normalizedAddress = request.address().toLowerCase(Locale.ROOT);

        if (walletRepository.existsByAddressAndNetwork(normalizedAddress, request.network())) {
            throw new DuplicateWalletException(normalizedAddress, request.network());
        }

        Wallet wallet = new Wallet();
        wallet.setAddress(normalizedAddress);
        wallet.setNetwork(request.network());
        wallet.setLabel(request.label());
        wallet = walletRepository.save(wallet);

        WalletSyncState syncState = new WalletSyncState();
        syncState.setWalletId(wallet.getId());
        syncStateRepository.save(syncState);

        return toResponse(wallet);
    }

    @Transactional
    public WalletResponse updateLabel(UUID walletId, UpdateWalletRequest request) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        wallet.setLabel(request.label());
        wallet = walletRepository.save(wallet);
        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
        return toResponse(wallet);
    }

    private WalletResponse toResponse(Wallet wallet) {
        return ImmutableWalletResponse.builder()
                .id(wallet.getId())
                .address(wallet.getAddress())
                .network(wallet.getNetwork())
                .label(wallet.getLabel())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}
