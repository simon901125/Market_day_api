package com.example.demo.dto.response;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Organizer equipment event summary")
public class OrganizerEquipmentSummaryResponse extends MapBackedResponse {

    public OrganizerEquipmentSummaryResponse(Map<String, Object> values) {
        super(values);
    }
}
