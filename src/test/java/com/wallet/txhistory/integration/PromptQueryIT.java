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

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("prompt-setup")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(page1))
                .willSetStateTo("done"));

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("prompt-setup")
                .whenScenarioStateIs("done")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(empty)));

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
                .andExpect(jsonPath("$.querySpec.direction").value("OUT"));
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
                .andExpect(jsonPath("$.querySpec.assets[0]").value("USDC"));
    }
}
