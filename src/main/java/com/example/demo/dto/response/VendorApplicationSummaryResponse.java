package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "攤主報名紀錄摘要")
public class VendorApplicationSummaryResponse extends MapBackedResponse {

    public VendorApplicationSummaryResponse(Map<String, Object> values) {
        super(values);
    }
}
