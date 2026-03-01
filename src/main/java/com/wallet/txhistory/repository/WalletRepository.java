package com.wallet.txhistory.repository;

import com.wallet.txhistory.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByAddressAndNetwork(String address, String network);
    boolean existsByAddressAndNetwork(String address, String network);
}
