package com.wallet.txhistory;

import com.wallet.txhistory.alchemy.AlchemyProperties;
import com.wallet.txhistory.config.EnrichmentProperties;
import com.wallet.txhistory.service.anthropic.PromptParserProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients
@EnableScheduling
@EnableConfigurationProperties({AlchemyProperties.class, PromptParserProperties.class, EnrichmentProperties.class})
public class WalletTransactionAppApplication {

	private static final Logger log = LoggerFactory.getLogger(WalletTransactionAppApplication.class);

	@Value("${server.port:8080}")
	private int serverPort;

	public static void main(String[] args) {
		SpringApplication.run(WalletTransactionAppApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void logStartupInfo() {
		String baseUrl = "http://localhost:" + serverPort;
		log.info("""

				=========================================================
				  Application is ready!
				  Base URL:  {}
				---------------------------------------------------------
				  API Endpoints:

				  Wallets:
				    POST   /v1/wallets
				    PATCH  /v1/wallets/{{walletId}}
				    POST   /v1/wallets/{{walletId}}/sync

				  Transactions:
				    GET    /v1/transactions
				    POST   /v1/transactions:query

				  Enrichment:
				    POST   /v1/transactions/explain
				---------------------------------------------------------
				  Swagger UI:      {}/swagger-ui.html
				  Actuator Health: {}/actuator/health
				=========================================================""", baseUrl, baseUrl, baseUrl);
	}

}
