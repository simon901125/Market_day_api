package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 攤主「我的報名紀錄」列表中的單筆活動報名資料。
 */
@Schema(description = "攤主活動報名列表項目")
public class VendorMarketSummaryResponse extends MapBackedResponse {

    public VendorMarketSummaryResponse(Map<String, Object> values) {
        super(values);
    }
}
