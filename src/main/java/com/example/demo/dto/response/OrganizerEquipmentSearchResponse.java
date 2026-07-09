package com.example.demo.dto.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Organizer equipment event search response")
public class OrganizerEquipmentSearchResponse {

    @Schema(description = "Total event count", example = "7")
    private int totalCount;

    @Schema(description = "Equipment event summaries")
    private List<OrganizerEquipmentSummaryResponse> events;

    public OrganizerEquipmentSearchResponse(List<OrganizerEquipmentSummaryResponse> events) {
        this.events = events;
        this.totalCount = events == null ? 0 : events.size();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public List<OrganizerEquipmentSummaryResponse> getEvents() {
        return events;
    }

    public void setEvents(List<OrganizerEquipmentSummaryResponse> events) {
        this.events = events;
    }
}
