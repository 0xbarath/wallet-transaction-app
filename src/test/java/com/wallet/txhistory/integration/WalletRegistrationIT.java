package com.wallet.txhistory.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WalletRegistrationIT extends BaseIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;

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
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").isNotEmpty());
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

        String id = objectMapper.readTree(response).get("id").asText();

        mockMvc.perform(patch("/v1/wallets/" + id)
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"new label"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("new label"))
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.address").value("0xcc00000000000000000000000000000000000003"))
                .andExpect(jsonPath("$.network").value("eth-mainnet"));
    }

    @Test
    void invalidAddressReturns400() throws Exception {
        mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"not-an-address","network":"eth-mainnet"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
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

    @Test
    void updateNonExistentWalletReturns404() throws Exception {
        String randomId = UUID.randomUUID().toString();

        mockMvc.perform(patch("/v1/wallets/" + randomId)
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"new label"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void registerWalletWithoutLabelSucceeds() throws Exception {
        mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"0xEE00000000000000000000000000000000000005","network":"eth-mainnet"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.address").value("0xee00000000000000000000000000000000000005"))
                .andExpect(jsonPath("$.network").value("eth-mainnet"))
                .andExpect(jsonPath("$.label").doesNotExist());
    }
}
