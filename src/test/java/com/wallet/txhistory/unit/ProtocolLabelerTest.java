package com.wallet.txhistory.unit;

import com.wallet.txhistory.dto.ReceiptLog;
import com.wallet.txhistory.dto.TransactionReceipt;
import com.wallet.txhistory.service.ProtocolLabeler;
import com.wallet.txhistory.model.AddressLabel;
import com.wallet.txhistory.repository.AddressLabelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProtocolLabelerTest {

    @Mock
    private AddressLabelRepository repository;

    private ProtocolLabeler labeler;

    @BeforeEach
    void setUp() {
        labeler = new ProtocolLabeler(repository);
    }

    @Test
    void returnsLabelForKnownAddress() {
        String address = "0x87870bca3f3fd6335c3f4ce8392d69350b4fa4e2";
        AddressLabel label = new AddressLabel();
        label.setAddress(address);
        label.setProtocol("aave-v3");
        label.setLabel("Aave V3: Pool");
        label.setCategory("lending");
        label.setSource("curated");
        label.setConfidence(new BigDecimal("0.9900"));

        when(repository.findByNetworkAndAddressIn(eq("eth-mainnet"), anyCollection()))
                .thenReturn(List.of(label));

        TransactionReceipt receipt = new TransactionReceipt(
                "0x1", "0x100",
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                address, null, "0x5208", List.of(),
                "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        );

        ProtocolLabeler.LabelResult result = labeler.labelAddresses("eth-mainnet", receipt);

        assertThat(result.hints()).hasSize(1);
        assertThat(result.hints().get(0).protocol()).isEqualTo("aave-v3");
        assertThat(result.hints().get(0).label()).isEqualTo("Aave V3: Pool");
        assertThat(result.evidenceItems()).hasSize(1);
        assertThat(result.evidenceItems().get(0).id()).isEqualTo("ev:label:to");
    }

    @Test
    void returnsEmptyForUnknownAddress() {
        when(repository.findByNetworkAndAddressIn(eq("eth-mainnet"), anyCollection()))
                .thenReturn(List.of());

        TransactionReceipt receipt = new TransactionReceipt(
                "0x1", "0x100",
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                null, "0x5208", List.of(),
                "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        );

        ProtocolLabeler.LabelResult result = labeler.labelAddresses("eth-mainnet", receipt);

        assertThat(result.hints()).isEmpty();
        assertThat(result.evidenceItems()).isEmpty();
    }
}
