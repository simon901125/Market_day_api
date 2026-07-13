package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 可報名市集的分頁查詢結果。 */
@Schema(description = "可報名市集查詢結果")
public class MarketSearchResponse {

    private final PageResponse<MarketSummaryResponse> markets;

    public MarketSearchResponse(PageResponse<MarketSummaryResponse> markets) {
        this.markets = markets;
    }

    public PageResponse<MarketSummaryResponse> getMarkets() {
        return markets;
    }
}
