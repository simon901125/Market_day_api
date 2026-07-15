package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 攤主活動報名詳細資料。
 */
@Schema(description = "攤主活動報名詳細資料")
public class VendorMarketDetailResponse extends MapBackedResponse {

  public VendorMarketDetailResponse(Map<String, Object> values) {
    super(values);
  }
}
