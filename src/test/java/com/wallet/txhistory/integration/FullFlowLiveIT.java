package com.wallet.txhistory.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full end-to-end live integration test covering all 6 REST endpoints.
 * Requires a .env file with ALCHEMY_RPC_URL and ANTHROPIC_API_KEY.
 * Excluded from normal test runs via @Tag("live").
 *
 * Run with: mvn test -Dsurefire.excludedGroups= -Dtest="FullFlowLiveIT" -pl .
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("live")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowLiveIT {

    private static final Properties dotenv = loadDotenvQuietly();

    private static final String AUTH_HEADER = "X-Auth-WalletAccess";
    private static final String AUTH_VALUE = "allow";
    private static final String ROLE_HEADER = "X-Role";

    private static final String WALLET_ADDRESS = "0x7e00c573fffc25a7721fa88e098d2f3de0a1feed";
    private static final String TX_HASH = "0x62ecf30b1bc21b15ede12076df45cfaa16bae581b1b765662c0bbbb60a847f2e";

    private static String walletId;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void checkCredentials() {
        assumeTrue(
                !dotenv.isEmpty()
                        && !dotenv.getProperty("ALCHEMY_RPC_URL", "").isBlank()
                        && !dotenv.getProperty("ANTHROPIC_API_KEY", "").isBlank(),
                "Skipping live test — .env with ALCHEMY_RPC_URL and ANTHROPIC_API_KEY required"
        );
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // H2 in-memory database with PostgreSQL compatibility mode
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:livetest_fullflow;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");

        // Real Alchemy
        registry.add("app.alchemy.rpc-url", () -> dotenv.getProperty("ALCHEMY_RPC_URL"));
        registry.add("app.alchemy.api-key", () -> dotenv.getProperty("ALCHEMY_API_KEY", ""));
        registry.add("app.alchemy.timeout", () -> "60s");

        // Real Anthropic
        String anthropicKey = dotenv.getProperty("ANTHROPIC_API_KEY", "");
        registry.add("app.prompt-parser.anthropic-api-key", () -> anthropicKey);
        registry.add("app.prompt-parser.timeout", () -> "60s");
        registry.add("app.enrichment.anthropic-api-key", () -> anthropicKey);
        registry.add("app.enrichment.timeout", () -> "60s");
    }

    @Test
    @Order(1)
    void registerWallet() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/wallets")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"address":"%s","network":"eth-mainnet","label":"live-test-wallet"}
                                """.formatted(WALLET_ADDRESS)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.address").value(WALLET_ADDRESS))
                .andExpect(jsonPath("$.network").value("eth-mainnet"))
                .andExpect(jsonPath("$.label").value("live-test-wallet"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        walletId = body.get("id").asText();
    }

    @Test
    @Order(2)
    void updateWalletLabel() throws Exception {
        assumeTrue(walletId != null, "Skipping — wallet registration did not succeed");

        mockMvc.perform(patch("/v1/wallets/{id}", walletId)
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"label":"updated-live-wallet"}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(walletId))
                .andExpect(jsonPath("$.label").value("updated-live-wallet"))
                .andExpect(jsonPath("$.address").value(WALLET_ADDRESS))
                .andExpect(jsonPath("$.network").value("eth-mainnet"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
    }

    @Test
    @Order(3)
    void syncWallet() throws Exception {
        assumeTrue(walletId != null, "Skipping — wallet registration did not succeed");

        mockMvc.perform(post("/v1/wallets/{id}/sync", walletId)
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lookbackDays":30}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.transfersSynced").isNumber())
                .andExpect(jsonPath("$.lastSyncedBlock").isNotEmpty())
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty());
    }

    @Test
    @Order(4)
    void queryTransactions() throws Exception {
        assumeTrue(walletId != null, "Skipping — wallet registration did not succeed");

        mockMvc.perform(get("/v1/transactions")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .param("walletId", walletId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.querySpec").exists())
                .andExpect(jsonPath("$.querySpec.walletId").value(walletId))
                .andExpect(jsonPath("$.querySpec.sort").isNotEmpty())
                .andExpect(jsonPath("$.querySpec.limit").isNumber())
                .andExpect(jsonPath("$.items[0].id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].network").value("eth-mainnet"))
                .andExpect(jsonPath("$.items[0].direction").isNotEmpty())
                .andExpect(jsonPath("$.items[0].asset").isNotEmpty());
    }

    @Test
    @Order(5)
    void promptQueryTransactions() throws Exception {
        assumeTrue(walletId != null, "Skipping — wallet registration did not succeed");

        mockMvc.perform(post("/v1/transactions:query")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"walletId":"%s","prompt":"show me recent ETH transfers"}
                                """.formatted(walletId)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.querySpec").exists())
                .andExpect(jsonPath("$.querySpec.walletId").value(walletId));
    }

    @Test
    @Order(6)
    void explainTransaction() throws Exception {
        mockMvc.perform(post("/v1/transactions/explain")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"txHash":"%s","network":"eth-mainnet","explain":true}
                                """.formatted(TX_HASH)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENRICHED"))
                .andExpect(jsonPath("$.explanation").exists())
                .andExpect(jsonPath("$.explanation.summary").isNotEmpty())
                .andExpect(jsonPath("$.explanation.steps").isArray())
                .andExpect(jsonPath("$.explanation.steps").isNotEmpty())
                .andExpect(jsonPath("$.txHash").value(TX_HASH))
                .andExpect(jsonPath("$.network").value("eth-mainnet"))
                .andExpect(jsonPath("$.evidence").isArray())
                .andExpect(jsonPath("$.evidence").isNotEmpty());
    }

    private static Properties loadDotenvQuietly() {
        Properties props = new Properties();
        Path envFile = Path.of(System.getProperty("user.dir"), ".env");
        if (!Files.exists(envFile)) {
            return props;
        }
        try {
            for (String line : Files.readAllLines(envFile)) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                if (eq > 0) {
                    String key = trimmed.substring(0, eq).strip();
                    String value = trimmed.substring(eq + 1).strip();
                    props.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            // Silently return empty properties — test will skip via assumeTrue
        }
        return props;
    }
}
