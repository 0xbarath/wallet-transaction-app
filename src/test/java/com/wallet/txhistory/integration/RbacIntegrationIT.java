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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RbacIntegrationIT extends BaseIntegrationTest {

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
                .inScenario("rbac-setup")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(page1))
                .willSetStateTo("done"));

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/alchemy"))
                .inScenario("rbac-setup")
                .whenScenarioStateIs("done")
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(empty)));

        String walletResponse = mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xCC33333333333333333333333333333333333333","network":"eth-mainnet"}
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
    void userCannotQueryInternalCategory() throws Exception {
        mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "user")
                        .param("walletId", walletId)
                        .param("category", "INTERNAL"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanQueryInternalCategory() throws Exception {
        mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .param("walletId", walletId)
                        .param("category", "INTERNAL"))
                .andExpect(status().isOk());
    }

    @Test
    void adminSeesFullAddresses() throws Exception {
        mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .param("walletId", walletId)
                        .param("limit", "50"))
                .andExpect(status().isOk());
    }
}
