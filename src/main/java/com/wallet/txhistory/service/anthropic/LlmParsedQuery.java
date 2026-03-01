package com.wallet.txhistory.service.anthropic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmParsedQuery(
        String direction,
        List<String> categories,
        List<String> assets,
        String minValue,
        String maxValue,
        String counterparty,
        String startTime,
        String endTime,
        String sort,
        Integer limit,
        List<String> needsClarification
) {
}
