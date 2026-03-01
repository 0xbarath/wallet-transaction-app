package com.wallet.txhistory.repository;

import com.wallet.txhistory.model.AddressLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressLabelRepository extends JpaRepository<AddressLabel, UUID> {

    List<AddressLabel> findByNetworkAndAddressIn(String network, Collection<String> addresses);

    Optional<AddressLabel> findByNetworkAndAddress(String network, String address);
}
