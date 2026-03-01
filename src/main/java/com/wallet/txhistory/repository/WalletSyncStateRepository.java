package com.wallet.txhistory.repository;

import com.wallet.txhistory.model.WalletSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WalletSyncStateRepository extends JpaRepository<WalletSyncState, UUID> {
}
