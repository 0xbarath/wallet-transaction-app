package com.wallet.txhistory.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.txhistory.dto.ImmutableOperationResult;
import com.wallet.txhistory.dto.ReceiptLog;
import com.wallet.txhistory.dto.TransactionReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class OperationClassifier {

    private static final Logger log = LoggerFactory.getLogger(OperationClassifier.class);

    private final Map<String, EventSignature> signatureMap;

    public OperationClassifier(ObjectMapper objectMapper) {
        try (InputStream is = new ClassPathResource("event-signatures.json").getInputStream()) {
            this.signatureMap = objectMapper.readValue(is,
                    new TypeReference<Map<String, EventSignature>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load event-signatures.json", e);
        }
    }

    public ImmutableOperationResult classify(TransactionReceipt receipt) {
        if (receipt.logs() == null || receipt.logs().isEmpty()) {
            return unknownResult();
        }

        for (int i = 0; i < receipt.logs().size(); i++) {
            ReceiptLog rl = receipt.logs().get(i);
            if (rl.topics() == null || rl.topics().isEmpty()) {
                continue;
            }
            String topic0 = rl.topics().get(0).toLowerCase();
            EventSignature sig = signatureMap.get(topic0);
            if (sig != null) {
                return ImmutableOperationResult.builder()
                        .name(sig.operation())
                        .confidence(0.9)
                        .evidenceIds(List.of("ev:log:" + i))
                        .build();
            }
        }

        return unknownResult();
    }

    private static ImmutableOperationResult unknownResult() {
        return ImmutableOperationResult.builder()
                .name("unknown")
                .confidence(0.0)
                .evidenceIds(List.of())
                .build();
    }

    public record EventSignature(String name, String protocol, String operation) {}
}
