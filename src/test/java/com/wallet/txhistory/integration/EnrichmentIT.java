package com.wallet.txhistory.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EnrichmentIT extends BaseIntegrationTest {

    private String receiptJson;
    private String receiptNullJson;
    private String anthropicJson;

    @BeforeEach
    void setUp() throws Exception {
        wireMock.resetAll();
        receiptJson = new String(getClass().getResourceAsStream("/wiremock/alchemy-receipt.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        receiptNullJson = new String(getClass().getResourceAsStream("/wiremock/alchemy-receipt-null.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        anthropicJson = new String(getClass().getResourceAsStream("/wiremock/anthropic-explain-response.json")
                .readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void adminCanExplainTransaction() throws Exception {
        stubAlchemyReceipt(receiptJson);

        // Stub Anthropic
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/v1/messages"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(anthropicJson)));

        mockMvc.perform(post("/v1/transactions/explain")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"txHash":"0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890","network":"eth-mainnet","explain":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txHash").value("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"))
                .andExpect(jsonPath("$.network").value("eth-mainnet"))
                .andExpect(jsonPath("$.status").value("ENRICHED"))
                .andExpect(jsonPath("$.operation.name").value("aave_supply"))
                .andExpect(jsonPath("$.protocolHints").isArray())
                .andExpect(jsonPath("$.evidence").isArray())
                .andExpect(jsonPath("$.evidence").isNotEmpty())
                .andExpect(jsonPath("$.explanation.summary").isNotEmpty())
                .andExpect(jsonPath("$.explanation.steps").isArray())
                .andExpect(jsonPath("$.explanation.steps").isNotEmpty())
                .andExpect(jsonPath("$.explanation.steps[0].text").isNotEmpty())
                .andExpect(jsonPath("$.explanation.steps[0].evidenceIds").isArray());
    }

    @Test
    void userRoleGetsForbidden() throws Exception {
        mockMvc.perform(post("/v1/transactions/explain")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"txHash":"0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void missingRoleGetsForbidden() throws Exception {
        // Default role is "user", so no X-Role header → forbidden
        mockMvc.perform(post("/v1/transactions/explain")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"txHash":"0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void nonExistentTxHashReturnsFailedStatus() throws Exception {
        stubAlchemyReceipt(receiptNullJson);

        mockMvc.perform(post("/v1/transactions/explain")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"txHash":"0x0000000000000000000000000000000000000000000000000000000000000000","network":"eth-mainnet"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.txHash").value("0x0000000000000000000000000000000000000000000000000000000000000000"))
                .andExpect(jsonPath("$.network").value("eth-mainnet"));
    }

    @Test
    void explainFalseSkipsLlm() throws Exception {
        stubAlchemyReceipt(receiptJson);

        mockMvc.perform(post("/v1/transactions/explain")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"txHash":"0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890","explain":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENRICHED"))
                .andExpect(jsonPath("$.explanation").doesNotExist())
                .andExpect(jsonPath("$.operation.name").value("aave_supply"));
    }

    private void stubAlchemyReceipt(String body) {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .withRequestBody(containing("eth_getTransactionReceipt"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
