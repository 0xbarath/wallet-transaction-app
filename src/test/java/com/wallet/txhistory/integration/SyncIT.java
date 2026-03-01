package com.wallet.txhistory.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SyncIT extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setupWireMock() throws IOException {
        wireMock.resetAll();

        String page1 = new String(getClass().getResourceAsStream("/wiremock/alchemy-transfers-page1.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        String page2 = new String(getClass().getResourceAsStream("/wiremock/alchemy-transfers-page2.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        String empty = new String(getClass().getResourceAsStream("/wiremock/alchemy-transfers-empty.json")
                .readAllBytes(), StandardCharsets.UTF_8);

        // First call returns page1 with pageKey, second returns page2 without
        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("sync")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(page1))
                .willSetStateTo("page1-done"));

        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("sync")
                .whenScenarioStateIs("page1-done")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(page2))
                .willSetStateTo("page2-done"));

        wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("sync")
                .whenScenarioStateIs("page2-done")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(empty)));
    }

    @Test
    void syncFetchesAndStoresTransfers() throws Exception {
        // Register wallet
        String walletResponse = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/wallets")
                                .header(AUTH_HEADER, AUTH_VALUE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"address":"0x1234567890abcdef1234567890abcdef12345678","network":"eth-mainnet"}
                                        """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String walletId = objectMapper.readTree(walletResponse).get("id").asText();

        // Sync
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/wallets/" + walletId + "/sync")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lookbackDays":30}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.transfersSynced").isNumber())
                .andExpect(jsonPath("$.lastSyncedBlock").isNumber())
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty());
    }
}
