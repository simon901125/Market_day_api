package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "品牌詳細資料")
public class BrandDetailResponse extends MapBackedResponse {

    public BrandDetailResponse(Map<String, Object> values) {
        super(values);
    }
}
