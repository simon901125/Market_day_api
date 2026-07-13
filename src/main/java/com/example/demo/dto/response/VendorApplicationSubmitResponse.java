package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 攤主送出活動報名後回傳的申請單摘要。
 */
@Schema(description = "攤主活動報名送出結果")
public class VendorApplicationSubmitResponse extends MapBackedResponse {

    public VendorApplicationSubmitResponse(Map<String, Object> values) {
        super(values);
    }
}
