package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "攤主報名詳情")
public class VendorApplicationDetailResponse extends MapBackedResponse {

    public VendorApplicationDetailResponse(Map<String, Object> values) {
        super(values);
    }
}
