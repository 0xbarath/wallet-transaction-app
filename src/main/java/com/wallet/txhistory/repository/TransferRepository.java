package com.wallet.txhistory.repository;

import com.wallet.txhistory.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID>, JpaSpecificationExecutor<Transfer> {

    @Query("SELECT t.uniqueId FROM Transfer t WHERE t.network = :network AND t.uniqueId IN :uniqueIds")
    Set<String> findExistingUniqueIds(@Param("network") String network,
                                      @Param("uniqueIds") Collection<String> uniqueIds);

    List<Transfer> findByHashAndNetwork(String hash, String network);
}
