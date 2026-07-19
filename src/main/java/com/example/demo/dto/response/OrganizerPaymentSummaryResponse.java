package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方付款列表單筆報名資料")
public class OrganizerPaymentSummaryResponse extends MapBackedResponse {
    public OrganizerPaymentSummaryResponse(Map<String, Object> values) {
        super(values);
    }
}
