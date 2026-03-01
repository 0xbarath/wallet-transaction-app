package com.wallet.txhistory.alchemy;

import com.wallet.txhistory.exception.AlchemyApiException;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

public class AlchemyFeignConfig {

    @Bean
    public Request.Options feignRequestOptions(AlchemyProperties properties) {
        long timeoutMs = properties.timeout().toMillis();
        return new Request.Options(timeoutMs, TimeUnit.MILLISECONDS, timeoutMs, TimeUnit.MILLISECONDS, true);
    }

    @Bean
    public Retryer feignRetryer(AlchemyProperties properties) {
        return new Retryer.Default(1000, 5000, properties.retryMaxAttempts());
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> {
            return new AlchemyApiException("Alchemy API error: HTTP " + response.status());
        };
    }
}
