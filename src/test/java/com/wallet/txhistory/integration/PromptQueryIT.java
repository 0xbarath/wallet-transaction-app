package com.wallet.txhistory.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PromptQueryIT extends BaseIntegrationTest {

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
        String anthropicOutEth = new String(getClass().getResourceAsStream("/wiremock/anthropic-prompt-parse-outgoing-eth.json")
                .readAllBytes(), StandardCharsets.UTF_8);
        String anthropicUsdc = new String(getClass().getResourceAsStream("/wiremock/anthropic-prompt-parse-usdc.json")
                .readAllBytes(), StandardCharsets.UTF_8);

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("prompt-setup")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(page1))
                .willSetStateTo("done"));

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("prompt-setup")
                .whenScenarioStateIs("done")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(empty)));

        // Anthropic stubs for prompt parsing
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("outgoing"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(anthropicOutEth)));

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/v1/messages"))
                .withRequestBody(containing("USDC"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(anthropicUsdc)));

        String walletResponse = mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xBB22222222222222222222222222222222222222","network":"eth-mainnet"}
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
    void promptQueryReturnsResults() throws Exception {
        mockMvc.perform(post("/v1/transactions:query")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"walletId":"%s","prompt":"show outgoing ETH transfers"}
                                """, walletId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.querySpec").isNotEmpty())
                .andExpect(jsonPath("$.querySpec.walletId").value(walletId))
                .andExpect(jsonPath("$.querySpec.direction").value("OUT"))
                .andExpect(jsonPath("$.querySpec.sort").value("createdAt_desc"));
    }

    @Test
    void promptQueryParsesAsset() throws Exception {
        mockMvc.perform(post("/v1/transactions:query")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"walletId":"%s","prompt":"show USDC transfers"}
                                """, walletId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.querySpec.walletId").value(walletId))
                .andExpect(jsonPath("$.querySpec.assets[0]").value("USDC"));
    }

    @Test
    void promptQueryWithMissingPromptReturns400() throws Exception {
        mockMvc.perform(post("/v1/transactions:query")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"walletId":"%s"}
                                """, walletId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void promptQueryWithMissingWalletIdReturns400() throws Exception {
        mockMvc.perform(post("/v1/transactions:query")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"show outgoing ETH transfers"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
