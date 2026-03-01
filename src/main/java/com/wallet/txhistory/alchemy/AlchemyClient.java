package com.wallet.txhistory.alchemy;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.wallet.txhistory.exception.AlchemyApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AlchemyClient {

    private static final Logger log = LoggerFactory.getLogger(AlchemyClient.class);

    private final AlchemyFeignClient feignClient;
    private final AlchemyProperties properties;
    private final Timer alchemyTimer;

    public AlchemyClient(AlchemyFeignClient feignClient, AlchemyProperties properties, MeterRegistry meterRegistry) {
        this.feignClient = feignClient;
        this.properties = properties;
        this.alchemyTimer = Timer.builder("alchemy.api.call")
                .description("Time for Alchemy API calls")
                .register(meterRegistry);
    }

    public AlchemyTransferPage getAssetTransfers(String address, String direction,
                                                  String fromBlock, String toBlock,
                                                  List<String> categories, String pageKey) {
        Timer.Sample sample = Timer.start();
        try {
            AssetTransferParams params = buildParams(address, direction, fromBlock, toBlock, categories, pageKey);
            JsonRpcRequest request = JsonRpcRequest.of(params);

            JsonRpcResponse response = feignClient.call(request);

            if (response.error() != null) {
                throw new AlchemyApiException("Alchemy error: " + response.error().message());
            }

            return toTransferPage(response.result());
        } catch (AlchemyApiException e) {
            throw e;
        } catch (Exception e) {
            throw new AlchemyApiException("Alchemy API call failed: " + e.getMessage(), e);
        } finally {
            sample.stop(alchemyTimer);
        }
    }

    private AssetTransferParams buildParams(String address, String direction,
                                             String fromBlock, String toBlock,
                                             List<String> categories, String pageKey) {
        String fromAddr = "OUT".equalsIgnoreCase(direction) ? address : null;
        String toAddr = "OUT".equalsIgnoreCase(direction) ? null : address;

        return new AssetTransferParams(
                fromAddr,
                toAddr,
                fromBlock != null ? fromBlock : "0x0",
                toBlock != null ? toBlock : "latest",
                categories != null ? categories : properties.categories(),
                "0x" + Integer.toHexString(properties.maxCount()),
                true,
                pageKey != null && !pageKey.isBlank() ? pageKey : null
        );
    }

    private AlchemyTransferPage toTransferPage(AssetTransferResult result) {
        if (result == null) {
            return new AlchemyTransferPage(List.of(), null);
        }

        List<AlchemyTransfer> transfers = result.transfers() != null ? result.transfers() : List.of();
        return new AlchemyTransferPage(transfers, result.pageKey());
    }
}
