package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "品牌列表項目")
public class BrandSummaryResponse extends MapBackedResponse {

    public BrandSummaryResponse(Map<String, Object> values) {
        super(values);
    }
}
