package com.wallet.txhistory.service;

import com.wallet.txhistory.dto.EvidenceBundle;
import com.wallet.txhistory.dto.ExplainRequest;
import com.wallet.txhistory.dto.ExplainResponse;
import com.wallet.txhistory.dto.ImmutableEvidenceItem;
import com.wallet.txhistory.dto.ImmutableExplainResponse;
import com.wallet.txhistory.dto.ImmutableExplanation;
import com.wallet.txhistory.dto.ImmutableLocalTransferSummary;
import com.wallet.txhistory.dto.ImmutableOperationResult;
import com.wallet.txhistory.dto.ImmutableProtocolHint;
import com.wallet.txhistory.exception.ForbiddenCategoryException;
import com.wallet.txhistory.filter.RbacFilter;
import com.wallet.txhistory.model.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TransactionExplainService {

    private static final Logger log = LoggerFactory.getLogger(TransactionExplainService.class);
    private static final String TX_HASH_PATTERN = "^0x[a-fA-F0-9]{64}$";

    private final EvidenceCollector evidenceCollector;
    private final ProtocolLabeler protocolLabeler;
    private final OperationClassifier operationClassifier;
    private final LlmExplainer llmExplainer;

    public TransactionExplainService(EvidenceCollector evidenceCollector,
                                      ProtocolLabeler protocolLabeler,
                                      OperationClassifier operationClassifier,
                                      LlmExplainer llmExplainer) {
        this.evidenceCollector = evidenceCollector;
        this.protocolLabeler = protocolLabeler;
        this.operationClassifier = operationClassifier;
        this.llmExplainer = llmExplainer;
    }

    public ExplainResponse explain(ExplainRequest request, String role) {
        // Defense-in-depth: verify admin role
        if (!RbacFilter.isAdmin(role)) {
            throw new ForbiddenCategoryException("Admin role required for transaction enrichment");
        }

        String txHash = request.txHash();
        String network = request.network();

        // Validate tx hash format
        if (!txHash.matches(TX_HASH_PATTERN)) {
            return ImmutableExplainResponse.builder()
                    .txHash(txHash)
                    .network(network)
                    .status("FAILED")
                    .protocolHints(List.of())
                    .operation(ImmutableOperationResult.builder()
                            .name("unknown").confidence(0.0).evidenceIds(List.of()).build())
                    .evidence(List.of())
                    .build();
        }

        // Collect evidence from chain + local DB
        EvidenceBundle bundle = evidenceCollector.collectEvidence(txHash, network);
        if (bundle == null) {
            return ImmutableExplainResponse.builder()
                    .txHash(txHash)
                    .network(network)
                    .status("FAILED")
                    .protocolHints(List.of())
                    .operation(ImmutableOperationResult.builder()
                            .name("unknown").confidence(0.0).evidenceIds(List.of()).build())
                    .evidence(List.of())
                    .build();
        }

        // Label addresses
        ProtocolLabeler.LabelResult labelResult = protocolLabeler.labelAddresses(network, bundle.receipt());
        List<ImmutableProtocolHint> hints = labelResult.hints();

        // Merge label evidence into evidence list
        List<ImmutableEvidenceItem> allEvidence = new ArrayList<>(bundle.evidenceItems());
        allEvidence.addAll(labelResult.evidenceItems());

        // Classify operation
        ImmutableOperationResult operation = operationClassifier.classify(bundle.receipt());

        // Build local transfer summaries
        List<ImmutableLocalTransferSummary> localTransfers = null;
        if (!bundle.localTransfers().isEmpty()) {
            localTransfers = new ArrayList<>();
            for (Transfer t : bundle.localTransfers()) {
                localTransfers.add(ImmutableLocalTransferSummary.builder()
                        .walletId(t.getWalletId().toString())
                        .direction(t.getDirection().name())
                        .asset(t.getAsset())
                        .value(t.getValueDecimal() != null ? t.getValueDecimal().toPlainString() : null)
                        .category(t.getCategory().name())
                        .blockNum(t.getBlockNum())
                        .build());
            }
        }

        // LLM explanation (optional)
        String status;
        ImmutableExplanation explanation = null;

        if (request.explain()) {
            Optional<ImmutableExplanation> llmResult = llmExplainer.explain(allEvidence, hints, operation);
            if (llmResult.isPresent()) {
                explanation = llmResult.get();
                status = "ENRICHED";
            } else {
                status = "PARTIAL";
            }
        } else {
            status = "ENRICHED";
        }

        // Human-readable format
        String humanReadable = null;
        if ("human".equals(request.format())) {
            humanReadable = buildHumanReadable(operation, hints, explanation);
        }

        return ImmutableExplainResponse.builder()
                .txHash(txHash)
                .network(network)
                .status(status)
                .protocolHints(hints)
                .operation(operation)
                .explanation(explanation)
                .evidence(allEvidence)
                .localTransfers(localTransfers)
                .humanReadable(humanReadable)
                .build();
    }

    private String buildHumanReadable(ImmutableOperationResult operation,
                                       List<ImmutableProtocolHint> hints,
                                       ImmutableExplanation explanation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Operation: ").append(operation.name());
        if (!hints.isEmpty()) {
            sb.append(" | Protocol: ").append(hints.get(0).protocol())
              .append(" (").append(hints.get(0).label()).append(")");
        }
        if (explanation != null) {
            sb.append("\n\nSummary: ").append(explanation.summary());
            if (!explanation.steps().isEmpty()) {
                sb.append("\n\nSteps:");
                for (int i = 0; i < explanation.steps().size(); i++) {
                    sb.append("\n  ").append(i + 1).append(". ").append(explanation.steps().get(i).text());
                }
            }
            if (!explanation.unknowns().isEmpty()) {
                sb.append("\n\nUnknowns: ").append(String.join("; ", explanation.unknowns()));
            }
        }
        return sb.toString();
    }
}
