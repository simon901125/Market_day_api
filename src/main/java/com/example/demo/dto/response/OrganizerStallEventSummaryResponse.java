package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "主辦方攤位管理活動摘要")
public class OrganizerStallEventSummaryResponse extends MapBackedResponse {

    public OrganizerStallEventSummaryResponse(Map<String, Object> values) {
        super(values);
    }
}
