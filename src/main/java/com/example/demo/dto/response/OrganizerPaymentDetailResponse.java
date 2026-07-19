package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方付款詳情")
public class OrganizerPaymentDetailResponse extends MapBackedResponse {

    public OrganizerPaymentDetailResponse(Map<String, Object> values) {
        super(values);
    }
}
