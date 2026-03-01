package com.wallet.txhistory.service.anthropic;

import com.wallet.txhistory.exception.PromptParseException;
import feign.Request;
import feign.RequestInterceptor;
import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class AnthropicFeignConfig {

    private static final Logger log = LoggerFactory.getLogger(AnthropicFeignConfig.class);

    @Bean
    public RequestInterceptor anthropicRequestInterceptor(PromptParserProperties properties) {
        return template -> {
            template.header("x-api-key", properties.anthropicApiKey());
            template.header("anthropic-version", "2023-06-01");
        };
    }

    @Bean
    public Request.Options anthropicRequestOptions(PromptParserProperties properties) {
        long timeoutMs = properties.timeout().toMillis();
        return new Request.Options(timeoutMs, TimeUnit.MILLISECONDS, timeoutMs, TimeUnit.MILLISECONDS, true);
    }

    @Bean
    public Retryer anthropicRetryer() {
        return new Retryer.Default(1000, 3000, 2);
    }

    @Bean
    public ErrorDecoder anthropicErrorDecoder() {
        return (methodKey, response) -> {
            int status = response.status();
            log.error("Upstream API error: method={} status={}", methodKey, status);
            drainResponseBody(response);

            if (status == 429 || status >= 500) {
                return new RetryableException(
                        status,
                        "Failed to process query — please try again",
                        response.request().httpMethod(),
                        new Date(),
                        response.request()
                );
            }

            return new PromptParseException("Failed to process query — please try again");
        };
    }

    private static void drainResponseBody(Response response) {
        try {
            if (response.body() != null) {
                response.body().close();
            }
        } catch (Exception ignored) {
            // best-effort drain
        }
    }
}
