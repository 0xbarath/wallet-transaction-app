package com.wallet.txhistory.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdempotentSyncIT extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setupWireMock() throws Exception {
        wireMock.resetAll();

        String page1 = new String(getClass().getResourceAsStream("/wiremock/alchemy-transfers-page1.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        String empty = new String(getClass().getResourceAsStream("/wiremock/alchemy-transfers-empty.json")
                .readAllBytes(), StandardCharsets.UTF_8);

        // Always return same page1 then empty
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("idempotent")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(page1))
                .willSetStateTo("after-first"));

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("idempotent")
                .whenScenarioStateIs("after-first")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(empty)));
    }

    @Test
    void syncTwiceNoDuplicates() throws Exception {
        String walletResponse = mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xEE00000000000000000000000000000000000005","network":"eth-mainnet"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String walletId = objectMapper.readTree(walletResponse).get("id").asText();

        // First sync
        mockMvc.perform(post("/v1/wallets/" + walletId + "/sync")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));

        // Reset WireMock for second sync (same data)
        wireMock.resetScenarios();

        // Second sync should not create duplicates
        mockMvc.perform(post("/v1/wallets/" + walletId + "/sync")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));
    }
}
