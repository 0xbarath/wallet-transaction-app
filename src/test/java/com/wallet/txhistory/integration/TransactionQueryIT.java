package com.wallet.txhistory.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TransactionQueryIT extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

    private String walletId;

    @BeforeEach
    void setupData() throws Exception {
        wireMock.resetAll();

        String page1 = new String(getClass().getResourceAsStream("/wiremock/alchemy-transfers-page1.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        String empty = new String(getClass().getResourceAsStream("/wiremock/alchemy-transfers-empty.json")
                .readAllBytes(), StandardCharsets.UTF_8);

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("query-setup")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(page1))
                .willSetStateTo("done"));

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("query-setup")
                .whenScenarioStateIs("done")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(empty)));

        // Register and sync a wallet
        String walletResponse = mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xAA11111111111111111111111111111111111111","network":"eth-mainnet"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        walletId = objectMapper.readTree(walletResponse).get("id").asText();

        mockMvc.perform(post("/v1/wallets/" + walletId + "/sync")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void queryReturnsTransfersWithAllFields() throws Exception {
        mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .param("walletId", walletId)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.querySpec.walletId").value(walletId))
                .andExpect(jsonPath("$.querySpec.sort").value("createdAt_desc"))
                .andExpect(jsonPath("$.querySpec.limit").value(10))
                .andExpect(jsonPath("$.items[0].id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].walletId").value(walletId))
                .andExpect(jsonPath("$.items[0].network").value("eth-mainnet"))
                .andExpect(jsonPath("$.items[0].hash").isNotEmpty())
                .andExpect(jsonPath("$.items[0].direction").isNotEmpty())
                .andExpect(jsonPath("$.items[0].asset").isNotEmpty())
                .andExpect(jsonPath("$.items[0].category").isNotEmpty())
                .andExpect(jsonPath("$.items[0].createdAt").isNotEmpty());
    }

    @Test
    void queryWithPaginationWorks() throws Exception {
        String firstPageResponse = mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .param("walletId", walletId)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Use cursor from first page to fetch second page
        JsonNode firstPage = objectMapper.readTree(firstPageResponse);
        String cursor = firstPage.get("nextCursor").asText();

        mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .param("walletId", walletId)
                        .param("limit", "1")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void queryWithAssetFilter() throws Exception {
        mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .param("walletId", walletId)
                        .param("asset", "ETH")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void queryWithDirectionFilter() throws Exception {
        mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .param("walletId", walletId)
                        .param("direction", "OUT")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.querySpec.direction").value("OUT"));
    }

    @Test
    void queryMissingWalletIdReturns400() throws Exception {
        mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE))
                .andExpect(status().isBadRequest());
    }
}
