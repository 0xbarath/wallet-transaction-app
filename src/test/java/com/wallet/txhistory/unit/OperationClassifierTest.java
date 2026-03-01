package com.wallet.txhistory.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.txhistory.dto.ImmutableOperationResult;
import com.wallet.txhistory.dto.ReceiptLog;
import com.wallet.txhistory.dto.TransactionReceipt;
import com.wallet.txhistory.service.OperationClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationClassifierTest {

    private OperationClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new OperationClassifier(new ObjectMapper());
    }

    @Test
    void classifiesAaveSupplyEvent() {
        ReceiptLog log = new ReceiptLog(
                "0x87870bca3f3fd6335c3f4ce8392d69350b4fa4e2",
                List.of("0x2b627736bca15cd5381dcf80b0bf11fd197d01a037c52b927a881a10fb73ba61"),
                "0x", 0, false
        );
        TransactionReceipt receipt = receipt(List.of(log));

        ImmutableOperationResult result = classifier.classify(receipt);

        assertThat(result.name()).isEqualTo("aave_supply");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.evidenceIds()).containsExactly("ev:log:0");
    }

    @Test
    void classifiesUniswapSwapEvent() {
        ReceiptLog log = new ReceiptLog(
                "0x68b3465833fb72a70ecdf485e0e4c7bd8665fc45",
                List.of("0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822"),
                "0x", 0, false
        );
        TransactionReceipt receipt = receipt(List.of(log));

        ImmutableOperationResult result = classifier.classify(receipt);

        assertThat(result.name()).isEqualTo("uniswap_swap");
        assertThat(result.confidence()).isEqualTo(0.9);
    }

    @Test
    void returnsUnknownForUnrecognizedTopic() {
        ReceiptLog log = new ReceiptLog(
                "0x0000000000000000000000000000000000000001",
                List.of("0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
                "0x", 0, false
        );
        TransactionReceipt receipt = receipt(List.of(log));

        ImmutableOperationResult result = classifier.classify(receipt);

        assertThat(result.name()).isEqualTo("unknown");
        assertThat(result.confidence()).isEqualTo(0.0);
    }

    @Test
    void returnsUnknownForEmptyLogs() {
        TransactionReceipt receipt = receipt(List.of());

        ImmutableOperationResult result = classifier.classify(receipt);

        assertThat(result.name()).isEqualTo("unknown");
    }

    @Test
    void picksFirstMatchingLog() {
        ReceiptLog unknown = new ReceiptLog(
                "0x0000000000000000000000000000000000000001",
                List.of("0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"),
                "0x", 0, false
        );
        ReceiptLog aaveSupply = new ReceiptLog(
                "0x87870bca3f3fd6335c3f4ce8392d69350b4fa4e2",
                List.of("0x2b627736bca15cd5381dcf80b0bf11fd197d01a037c52b927a881a10fb73ba61"),
                "0x", 1, false
        );
        TransactionReceipt receipt = receipt(List.of(unknown, aaveSupply));

        ImmutableOperationResult result = classifier.classify(receipt);

        assertThat(result.name()).isEqualTo("aave_supply");
        assertThat(result.evidenceIds()).containsExactly("ev:log:1");
    }

    private static TransactionReceipt receipt(List<ReceiptLog> logs) {
        return new TransactionReceipt(
                "0x1", "0x100",
                "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                null, "0x5208", logs,
                "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        );
    }
}
