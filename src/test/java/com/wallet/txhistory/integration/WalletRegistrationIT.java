package com.wallet.txhistory.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalletRegistrationIT extends BaseIntegrationTest {

    @Test
    void registerWallet() throws Exception {
        mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xAA00000000000000000000000000000000000001","network":"eth-mainnet","label":"test"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.address").value("0xaa00000000000000000000000000000000000001"))
                .andExpect(jsonPath("$.network").value("eth-mainnet"))
                .andExpect(jsonPath("$.label").value("test"))
                .andExpect(jsonPath("$.id").isNotEmpty());
    }

    @Test
    void duplicateWalletReturnsConflict() throws Exception {
        mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xBB00000000000000000000000000000000000002","network":"eth-mainnet"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xBB00000000000000000000000000000000000002","network":"eth-mainnet"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void updateLabel() throws Exception {
        String response = mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xCC00000000000000000000000000000000000003","network":"eth-mainnet","label":"old"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String id = com.fasterxml.jackson.databind.ObjectMapper.class.getDeclaredConstructor()
                .newInstance().readTree(response).get("id").asText();

        mockMvc.perform(patch("/v1/wallets/" + id)
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"new label"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("new label"));
    }

    @Test
    void invalidAddressReturns400() throws Exception {
        mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"not-an-address","network":"eth-mainnet"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingAuthReturns401() throws Exception {
        mockMvc.perform(post("/v1/wallets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xDD00000000000000000000000000000000000004","network":"eth-mainnet"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
