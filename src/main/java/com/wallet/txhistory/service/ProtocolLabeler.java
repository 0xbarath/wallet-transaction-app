package com.wallet.txhistory.service;

import com.wallet.txhistory.dto.ImmutableEvidenceItem;
import com.wallet.txhistory.dto.ImmutableProtocolHint;
import com.wallet.txhistory.dto.ReceiptLog;
import com.wallet.txhistory.dto.TransactionReceipt;
import com.wallet.txhistory.model.AddressLabel;
import com.wallet.txhistory.repository.AddressLabelRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ProtocolLabeler {

    private final AddressLabelRepository addressLabelRepository;

    public ProtocolLabeler(AddressLabelRepository addressLabelRepository) {
        this.addressLabelRepository = addressLabelRepository;
    }

    public LabelResult labelAddresses(String network, TransactionReceipt receipt) {
        Set<String> addresses = new LinkedHashSet<>();
        if (receipt.from() != null) addresses.add(receipt.from().toLowerCase());
        if (receipt.to() != null) addresses.add(receipt.to().toLowerCase());
        if (receipt.logs() != null) {
            for (ReceiptLog log : receipt.logs()) {
                if (log.address() != null) {
                    addresses.add(log.address().toLowerCase());
                }
            }
        }

        if (addresses.isEmpty()) {
            return new LabelResult(List.of(), List.of());
        }

        List<AddressLabel> labels = addressLabelRepository.findByNetworkAndAddressIn(network, addresses);

        List<ImmutableProtocolHint> hints = new ArrayList<>();
        List<ImmutableEvidenceItem> evidenceItems = new ArrayList<>();

        for (AddressLabel label : labels) {
            String evidenceId = resolveEvidenceId(label.getAddress(), receipt);

            hints.add(ImmutableProtocolHint.builder()
                    .address(label.getAddress())
                    .protocol(label.getProtocol())
                    .label(label.getLabel())
                    .confidence(label.getConfidence().doubleValue())
                    .source(label.getSource())
                    .category(label.getCategory())
                    .build());

            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("address", label.getAddress());
            fields.put("protocol", label.getProtocol());
            fields.put("label", label.getLabel());
            fields.put("category", label.getCategory());
            fields.put("confidence", label.getConfidence().doubleValue());

            evidenceItems.add(ImmutableEvidenceItem.builder()
                    .id(evidenceId)
                    .type("address_label")
                    .fields(fields)
                    .build());
        }

        return new LabelResult(hints, evidenceItems);
    }

    private String resolveEvidenceId(String address, TransactionReceipt receipt) {
        if (address.equalsIgnoreCase(receipt.from())) {
            return "ev:label:from";
        }
        if (address.equalsIgnoreCase(receipt.to())) {
            return "ev:label:to";
        }
        return "ev:label:" + address;
    }

    public record LabelResult(
            List<ImmutableProtocolHint> hints,
            List<ImmutableEvidenceItem> evidenceItems
    ) {}
}
