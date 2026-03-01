package com.wallet.txhistory.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Tag("live")
class UniswapExplainLiveIT {

    private static final Properties dotenv = loadDotenvQuietly();

    @Autowired
    private MockMvc mockMvc;

    private static final String AUTH_HEADER = "X-Auth-WalletAccess";
    private static final String AUTH_VALUE = "allow";
    private static final String ROLE_HEADER = "X-Role";

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
        // H2 in-memory database (Liquibase handles schema)
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:livetest;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");

        // Real Alchemy
        registry.add("app.alchemy.rpc-url", () -> dotenv.getProperty("ALCHEMY_RPC_URL"));
        registry.add("app.alchemy.api-key", () -> dotenv.getProperty("ALCHEMY_API_KEY", ""));

        // Real Anthropic — needed by both prompt-parser and enrichment
        String anthropicKey = dotenv.getProperty("ANTHROPIC_API_KEY", "");
        registry.add("app.prompt-parser.anthropic-api-key", () -> anthropicKey);
        registry.add("app.enrichment.anthropic-api-key", () -> anthropicKey);
    }

    @Test
    void uniswapSwapReturnsNonNullExplanation() throws Exception {
        mockMvc.perform(post("/v1/transactions/explain")
                        .header(AUTH_HEADER, AUTH_VALUE)
                        .header(ROLE_HEADER, "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"txHash":"0x62ecf30b1bc21b15ede12076df45cfaa16bae581b1b765662c0bbbb60a847f2e","network":"eth-mainnet","explain":true}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENRICHED"))
                .andExpect(jsonPath("$.explanation").exists())
                .andExpect(jsonPath("$.explanation.summary").isNotEmpty())
                .andExpect(jsonPath("$.explanation.steps").isArray())
                .andExpect(jsonPath("$.explanation.steps").isNotEmpty());
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
