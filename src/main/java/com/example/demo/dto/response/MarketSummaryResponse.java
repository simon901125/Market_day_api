package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/** 攤主專區「市集報名」列表中的單張市集卡片。 */
@Schema(description = "可報名市集摘要")
public class MarketSummaryResponse extends MapBackedResponse {

    public MarketSummaryResponse(Map<String, Object> values) {
        super(values);
    }
}
