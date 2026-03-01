package com.wallet.txhistory.service.anthropic;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "anthropic",
        url = "${app.anthropic.base-url:https://api.anthropic.com}",
        configuration = AnthropicFeignConfig.class
)
public interface AnthropicFeignClient {

    @PostMapping(value = "/v1/messages", consumes = "application/json", produces = "application/json")
    AnthropicResponse sendMessage(@RequestBody AnthropicRequest request);
}
