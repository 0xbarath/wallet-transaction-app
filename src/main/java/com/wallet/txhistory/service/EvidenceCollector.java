package com.wallet.txhistory.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.txhistory.alchemy.AlchemyFeignClient;
import com.wallet.txhistory.alchemy.GenericJsonRpcRequest;
import com.wallet.txhistory.alchemy.GenericJsonRpcResponse;
import com.wallet.txhistory.dto.EvidenceBundle;
import com.wallet.txhistory.dto.ImmutableEvidenceItem;
import com.wallet.txhistory.dto.ReceiptLog;
import com.wallet.txhistory.dto.TransactionReceipt;
import com.wallet.txhistory.model.Transfer;
import com.wallet.txhistory.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class EvidenceCollector {

    private static final Logger log = LoggerFactory.getLogger(EvidenceCollector.class);
    private static final int MAX_LOGS = 50;
    private static final int MAX_DATA_LENGTH = 1024;

    private final AlchemyFeignClient alchemyClient;
    private final TransferRepository transferRepository;
    private final ObjectMapper objectMapper;

    public EvidenceCollector(AlchemyFeignClient alchemyClient,
                             TransferRepository transferRepository,
                             ObjectMapper objectMapper) {
        this.alchemyClient = alchemyClient;
        this.transferRepository = transferRepository;
        this.objectMapper = objectMapper;
    }

    public EvidenceBundle collectEvidence(String txHash, String network) {
        TransactionReceipt receipt = fetchReceipt(txHash);
        if (receipt == null) {
            return null;
        }

        List<ImmutableEvidenceItem> evidenceItems = new ArrayList<>();

        // ev:tx — transaction-level evidence
        Map<String, Object> txFields = new LinkedHashMap<>();
        Optional.ofNullable(receipt.from()).ifPresent(v -> txFields.put("from", v));
        Optional.ofNullable(receipt.to()).ifPresent(v -> txFields.put("to", v));
        Optional.ofNullable(receipt.status()).ifPresent(v -> txFields.put("status", v));
        Optional.ofNullable(receipt.blockNumber()).ifPresent(v -> txFields.put("blockNumber", v));
        Optional.ofNullable(receipt.gasUsed()).ifPresent(v -> txFields.put("gasUsed", v));
        Optional.ofNullable(receipt.contractAddress()).ifPresent(v -> txFields.put("contractAddress", v));
        Optional.ofNullable(receipt.transactionHash()).ifPresent(v -> txFields.put("transactionHash", v));
        evidenceItems.add(ImmutableEvidenceItem.builder()
                .id("ev:tx")
                .type("tx")
                .fields(txFields)
                .build());

        // ev:log:N — one per log
        List<ReceiptLog> logs = receipt.logs();
        if (logs != null) {
            int logCount = Math.min(logs.size(), MAX_LOGS);
            for (int i = 0; i < logCount; i++) {
                ReceiptLog rl = logs.get(i);
                Map<String, Object> logFields = new LinkedHashMap<>();
                Optional.ofNullable(rl.address()).ifPresent(v -> logFields.put("address", v));
                Optional.ofNullable(rl.topics()).ifPresent(v -> logFields.put("topics", v));
                String data = rl.data();
                if (data != null) {
                    if (data.length() > MAX_DATA_LENGTH) {
                        data = data.substring(0, MAX_DATA_LENGTH);
                    }
                    logFields.put("data", data);
                }
                logFields.put("logIndex", rl.logIndex());
                evidenceItems.add(ImmutableEvidenceItem.builder()
                        .id("ev:log:" + i)
                        .type("log")
                        .fields(logFields)
                        .build());
            }
        }

        // Local DB transfers
        List<Transfer> localTransfers = transferRepository.findByHashAndNetwork(txHash, network);
        for (int i = 0; i < localTransfers.size(); i++) {
            Transfer t = localTransfers.get(i);
            Map<String, Object> tFields = new LinkedHashMap<>();
            Optional.ofNullable(t.getWalletId()).ifPresent(v -> tFields.put("walletId", v.toString()));
            Optional.ofNullable(t.getDirection()).ifPresent(v -> tFields.put("direction", v.name()));
            Optional.ofNullable(t.getAsset()).ifPresent(v -> tFields.put("asset", v));
            Optional.ofNullable(t.getValueDecimal()).ifPresent(v -> tFields.put("value", v.toPlainString()));
            Optional.ofNullable(t.getCategory()).ifPresent(v -> tFields.put("category", v.name()));
            Optional.ofNullable(t.getBlockNum()).ifPresent(v -> tFields.put("blockNum", v));
            evidenceItems.add(ImmutableEvidenceItem.builder()
                    .id("ev:transfer:" + i)
                    .type("transfer")
                    .fields(tFields)
                    .build());
        }

        return new EvidenceBundle(receipt, evidenceItems, localTransfers);
    }

    private TransactionReceipt fetchReceipt(String txHash) {
        GenericJsonRpcRequest request = GenericJsonRpcRequest.of("eth_getTransactionReceipt", txHash);
        GenericJsonRpcResponse response = alchemyClient.callGeneric(request);

        if (response.error() != null) {
            log.warn("RPC error fetching receipt for {}: {}", txHash, response.error().message());
            return null;
        }

        JsonNode result = response.result();
        if (result == null || result.isNull()) {
            log.info("No receipt found for tx hash: {}", txHash);
            return null;
        }

        return parseReceipt(result);
    }

    private TransactionReceipt parseReceipt(JsonNode node) {
        List<ReceiptLog> logs = new ArrayList<>();
        JsonNode logsNode = node.get("logs");
        if (logsNode != null && logsNode.isArray()) {
            for (JsonNode logNode : logsNode) {
                List<String> topics = new ArrayList<>();
                JsonNode topicsNode = logNode.get("topics");
                if (topicsNode != null && topicsNode.isArray()) {
                    for (JsonNode t : topicsNode) {
                        topics.add(t.asText());
                    }
                }
                logs.add(new ReceiptLog(
                        textOrNull(logNode, "address"),
                        topics,
                        textOrNull(logNode, "data"),
                        hexToInt(textOrNull(logNode, "logIndex")),
                        logNode.has("removed") && logNode.get("removed").asBoolean()
                ));
            }
        }

        return new TransactionReceipt(
                textOrNull(node, "status"),
                textOrNull(node, "blockNumber"),
                textOrNull(node, "from"),
                textOrNull(node, "to"),
                textOrNull(node, "contractAddress"),
                textOrNull(node, "gasUsed"),
                logs,
                textOrNull(node, "transactionHash")
        );
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        return child.asText();
    }

    private static int hexToInt(String hex) {
        if (hex == null) return 0;
        if (hex.startsWith("0x")) {
            return Integer.parseInt(hex.substring(2), 16);
        }
        return Integer.parseInt(hex);
    }
}
