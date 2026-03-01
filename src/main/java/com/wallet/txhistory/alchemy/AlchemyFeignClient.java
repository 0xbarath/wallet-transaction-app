package com.wallet.txhistory.alchemy;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "alchemy",
        url = "${app.alchemy.rpc-url:}",
        configuration = AlchemyFeignConfig.class
)
public interface AlchemyFeignClient {

    @PostMapping(consumes = "application/json", produces = "application/json")
    JsonRpcResponse call(@RequestBody JsonRpcRequest request);

    @PostMapping(consumes = "application/json", produces = "application/json")
    GenericJsonRpcResponse callGeneric(@RequestBody GenericJsonRpcRequest request);
}
